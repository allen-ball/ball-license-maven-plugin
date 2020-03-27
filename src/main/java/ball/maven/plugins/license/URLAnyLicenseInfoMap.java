package ball.maven.plugins.license;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {@link URL} ({@link String} representation) to {@link AnyLicenseInfo}
 * {@link java.util.Map} implementation.  The {@link #get(Object)} method
 * transparently calculates and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@NoArgsConstructor @Slf4j
public class URLAnyLicenseInfoMap extends TreeMap<String,AnyLicenseInfo> {
    private static final HostnameVerifier NONE = new HostnameVerifierImpl();

    private static final Set<Integer> REDIRECT_CODES =
        Stream.of(HttpURLConnection.HTTP_MOVED_TEMP,
                  HttpURLConnection.HTTP_MOVED_PERM,
                  HttpURLConnection.HTTP_SEE_OTHER)
        .collect(toSet());

    private static final Pattern CANONICAL =
        Pattern.compile("<([^>]*)>; rel=\"canonical\"");

    @Inject private LicenseMap licenseMap = null;
    @Inject private AnyLicenseInfoFactory anyLicenseInfoFactory = null;

    @PostConstruct
    public void init() {
        try {
            for (License value : licenseMap.values()) {
                for (String key : value.getSeeAlso()) {
                    if (! containsKey(key)) {
                        put(key, value);
                    }
                }
            }

            put("https://glassfish.dev.java.net/public/CDDLv1.0.html",
                licenseMap.get("CDDL-1.0"));
            put("https://www.mozilla.org/MPL/MPL-1.0.txt",
                licenseMap.get("MPL-1.0"));
            /*
             * FileNotFoundException heuristic:
             * www.mozilla.org -> www-archive.mozilla.org
             */
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    /**
     * Entry-point that first attempts to parse {@code name} as an SPDX
     * license ID and, if that fails, continues to analyze the document at
     * {@code url}.
     *
     * @param   name            The observed name of the license.
     * @param   url             The {@code URL} of the license document.
     *
     * @return  {@link License} if {@code name} can be parsed; the result of
     *          {@link #get(Object)} otherwise.
     */
    public AnyLicenseInfo parse(String name, String url) {
        AnyLicenseInfo value = anyLicenseInfoFactory.get(name, null);

        if (value == null || value instanceof ExtractedLicenseInfo) {
            if (isNotBlank(url)) {
                value = get(name, url);
            }
        }

        return value;
    }

    private AnyLicenseInfo get(String name, String url) {
        AnyLicenseInfo value = super.get(url);

        if (value == null) {
            value = compute(name, url);

            put(url, value);
        }

        return value;
    }

    @Override
    public AnyLicenseInfo get(Object key) {
        return get(null, key.toString());
    }

    private AnyLicenseInfo compute(String name, String url) {
        AnyLicenseInfo value = null;
        String text = null;

        try {
            URLConnection connection = new URL(url).openConnection();

            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NONE);
            }

            String canonicalURL = getCanonicalURL(connection);

            if (isNotBlank(canonicalURL) && (! canonicalURL.equals(url))) {
                value = get(name, canonicalURL);
            }

            String redirectURL = getRedirectURL(connection);

            if (isNotBlank(redirectURL)) {
                value = get(name, redirectURL);
            }

            if (value == null) {
                try (InputStream in = connection.getInputStream()) {
                    Document document = Jsoup.parse(in, null, url);

                    text = document.body().text();
                }
            }
        } catch (FileNotFoundException exception) {
            log.warn("File not found: " + url);
        } catch (Exception exception) {
            log.warn("Cannot read " + url);
            log.debug(exception.getMessage(), exception);
        } finally {
            if (value == null) {
                value =
                    anyLicenseInfoFactory
                    .get(isNotBlank(name) ? name : url, text);
            }

            if (value instanceof ExtractedLicenseInfo) {
                log.warn("Cannot find SPDX license for"
                         + (isNotBlank(name) ? (" '" + name + "'") : "")
                         + (isNotBlank(url) ? (" @ " + url) : ""));
            }
        }

        if (value instanceof ExtractedLicenseInfo) {
            ExtractedLicenseInfo license = (ExtractedLicenseInfo) value;
            String[] seeAlso = license.getSeeAlso();
            Set<String> set =
                Stream.of((seeAlso != null) ? seeAlso : new String[] { })
                .collect(toSet());

            set.add(url);
            license.setSeeAlso(set.toArray(new String[] { }));
        }

        return value;
    }

    private String getCanonicalURL(URLConnection connection) {
        String url = null;

        if (connection instanceof HttpURLConnection) {
            List<String> list = connection.getHeaderFields().get("Link");

            if (list != null) {
                url =
                    list.stream()
                    .map(t -> CANONICAL.matcher(t))
                    .filter(t -> t.find())
                    .map(t -> t.group(1))
                    .map(t -> URI.create(connection.getURL().toString())
                              .resolve(t).toASCIIString())
                    .findFirst().orElse(null);
            }
        }

        return url;
    }

    private String getRedirectURL(URLConnection connection) throws IOException {
        String url = null;

        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();

            if (REDIRECT_CODES.contains(code)) {
                url = connection.getHeaderField("Location");
            }
        }

        return url;
    }

    @NoArgsConstructor @ToString
    private static class HostnameVerifierImpl implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
