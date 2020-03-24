package ball.maven.plugins.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.ListedLicenses;
import org.spdx.rdfparser.license.SpdxNoneLicense;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.spdx.compare.LicenseCompareHelper.matchingStandardLicenseIds;
import static org.spdx.rdfparser.license.LicenseInfoFactory.parseSPDXLicenseString;

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

    private static final ListedLicenses LISTED_LICENSES =
        ListedLicenses.getListedLicenses();

    private static final TreeMap<String,License> listedLicenseMap =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    @PostConstruct
    public void init() {
        try {
            for (String id : LISTED_LICENSES.getSpdxListedLicenseIds()) {
                License value = LISTED_LICENSES.getListedLicenseById(id);

                listedLicenseMap.put(id, value);

                for (String key : value.getSeeAlso()) {
                    if (! containsKey(key)) {
                        put(key, value);
                    }
                }
            }

            URL url =
                getClass().getClassLoader()
                .getResource("resources/licenses-full.json");
            for (JsonNode node :
                     new ObjectMapper().readTree(url).at("/licenses")) {
                JsonNode keys = node.at("/uris");
                String id = node.at("/identifiers/spdx[0]").asText();

                if (isNotEmpty(id)) {
                    License value = LISTED_LICENSES.getListedLicenseById(id);

                    for (JsonNode key : keys) {
                        if (! containsKey(key.asText())) {
                            put(key.asText(), value);
                        }
                    }
                }
            }

            put("https://glassfish.dev.java.net/public/CDDLv1.0.html",
                LISTED_LICENSES.getListedLicenseById("CDDL-1.0"));
            put("https://www.mozilla.org/MPL/MPL-1.0.txt",
                LISTED_LICENSES.getListedLicenseById("MPL-1.0"));
            /*
             * FileNotFoundException heuristic:
             * www.mozilla.org -> www-archive.mozilla.org
             */
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @PreDestroy
    public void destroy() { }

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
        AnyLicenseInfo value = null;

        if (value == null) {
            if (isNotBlank(name)) {
                if (value == null) {
                    try {
                        value =
                            listedLicenseMap
                            .get(name.replaceAll("[\\p{Space}]", "-"));
                    } catch (Exception exception) {
                    }
                }

                if (value == null) {
                    try {
                        value = (License) parseSPDXLicenseString(name);
                    } catch (Exception exception) {
                    }
                }
            }
        }

        if (value == null) {
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

        try {
            URLConnection connection = new URL(url).openConnection();

            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NONE);
            }

            String redirectURL = getRedirectURL(connection);
            String canonicalURL = getCanonicalURL(connection);

            if (redirectURL != null) {
                value = get(name, redirectURL);
            } else {
                if (canonicalURL != null && (! canonicalURL.equals(url))) {
                    value = get(name, canonicalURL);
                } else {
                    String text = null;

                    try (InputStream in = connection.getInputStream()) {
                        text =
                            new BufferedReader(new InputStreamReader(in, UTF_8))
                            .lines()
                            .collect(joining(LF, EMPTY, LF));

                        String[] ids = matchingStandardLicenseIds(text);

                        value = parseSPDXLicenseString(ids[0]);
                    } catch (FileNotFoundException exception) {
                        log.warn("File not found: " + url);
                    } catch (IOException exception) {
                        log.warn("Cannot read " + url);
                        log.debug(exception.getMessage(), exception);
                    } catch (Exception exception) {
                    }

                    if (value == null) {
                        if (isNotBlank(text)) {
                            value =
                                new ExtractedLicenseInfo(name, text, name,
                                                         new String[] { url },
                                                         null);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("Cannot read " + url);
            log.debug(exception.getMessage(), exception);
        }

        if (value == null) {
            value = new SpdxNoneLicense();
        }

        return value;
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

    @NoArgsConstructor @ToString
    private static class HostnameVerifierImpl implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
