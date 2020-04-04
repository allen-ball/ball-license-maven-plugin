package ball.maven.plugins.license;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
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
@Slf4j
public class URLLicenseInfoParser extends TreeMap<String,AnyLicenseInfo> {
    private static final HostnameVerifier NONE = new HostnameVerifierImpl();

    private static final Set<Integer> REDIRECT_CODES =
        Stream.of(HttpURLConnection.HTTP_MOVED_TEMP,
                  HttpURLConnection.HTTP_MOVED_PERM,
                  HttpURLConnection.HTTP_SEE_OTHER)
        .collect(toSet());

    private static final Pattern CANONICAL =
        Pattern.compile("<([^>]*)>; rel=\"canonical\"");

    /** @serial */ private final LicenseMap map;
    /** @serial */ private final TextLicenseInfoParser parser;
    /** @serial */ private final Map<Pattern,String> redirects;

    /**
     * Sole constructor.
     *
     * @param   map             The injected {@link LicenseMap}.
     * @param   parser          The injected {@link TextLicenseInfoParser}.
     */
    @Inject
    public URLLicenseInfoParser(LicenseMap map, TextLicenseInfoParser parser) {
        super();

        this.map = Objects.requireNonNull(map);
        this.parser = Objects.requireNonNull(parser);

        try {
            for (License value : map.values()) {
                for (String key : value.getSeeAlso()) {
                    put(String.format("https://spdx.org/licenses/%s.html",
                                      value.getLicenseId()),
                        value);
                    put(key, value);
                }
            }

            for (JsonNode node :
                     LicenseMap.LICENSES_FULL_JSON.at("/licenses")) {
                AnyLicenseInfo value =
                    map.get(node.at("/identifiers/spdx/0").asText());

                if (value != null) {
                    for (JsonNode uri : node.at("/uris")) {
                        String key = uri.asText();

                        putIfAbsent(key, value);
                    }
                }
            }

            Properties seeds = getXMLProperties("seeds");

            for (String id : seeds.stringPropertyNames()) {
                AnyLicenseInfo value = parseSPDXLicenseString(id);

                for (String key :
                         seeds.getProperty(id).trim().split("[\\p{Space}]+")) {
                    if (isNotBlank(key)) {
                        putIfAbsent(key, value);
                    }
                }
            }

            redirects =
                getXMLProperties("redirects").entrySet()
                .stream()
                .collect(toMap(k -> Pattern.compile(k.getKey().toString()),
                               v -> v.getValue().toString().trim()));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private Properties getXMLProperties(String name) throws Exception {
        Properties properties = new Properties();
        String resource =
            String.format("%1$s.%2$s.xml",
                          getClass().getSimpleName(), name);

        try (InputStream in = getClass().getResourceAsStream(resource)) {
            properties.loadFromXML(in);
        }

        return properties;
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    /**
     * Entry-point that first attempts to parse the specified license ID as
     * an SPDX license ID and, if that fails, continues to analyze the
     * document at specified URL.
     *
     * @param   resolver        The {@link LicenseResolver}.
     * @param   key             The {@link URLLicenseInfo}.
     *
     * @return  {@link AnyLicenseInfo} reppresenting the results of the
     *          parse (may be {@code key}).
     */
    public AnyLicenseInfo parse(LicenseResolver resolver, URLLicenseInfo key) {
        String name = key.getLicenseId();
        AnyLicenseInfo value = isNotBlank(name) ? map.get(name) : null;

        if (value == null) {
            Set<AnyLicenseInfo> set =
                Stream.of(key.getSeeAlso())
                .map(t -> computeIfAbsent(t, k -> compute(resolver, k)))
                .collect(toSet());

            if (! set.isEmpty()) {
                value = resolver.toLicense(set);
            }
        }

        return (value != null) ? value : key;
    }

    private AnyLicenseInfo compute(LicenseResolver resolver, String url) {
        AnyLicenseInfo value = null;

        try {
            URLConnection connection = new URL(url).openConnection();

            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NONE);
            }

            String canonicalURL = getCanonicalURL(connection);

            if (isNotBlank(canonicalURL) && (! canonicalURL.equals(url))) {
                if (value == null) {
                    value = get(canonicalURL);
                }
            }

            String redirectURL = getRedirectURL(connection);

            if (isNotBlank(redirectURL)) {
                if (! redirectURL.equals(url)) {
                    if (value != null) {
                        put(redirectURL, value);
                    } else {
                        value =
                            computeIfAbsent(redirectURL,
                                            k -> compute(resolver, k));
                    }
                }
            }

            if (value == null) {
                Document document = null;

                try (InputStream in = connection.getInputStream()) {
                    document = Jsoup.parse(in, null, url);
                    document.outputSettings()
                        .syntax(Document.OutputSettings.Syntax.xml);
                }
                /*
                 * Heuristic: Look for a "canonical" <link/> with a known
                 * href.
                 */
                value =
                    document.select("head>link[rel='canonical'][href]")
                    .stream()
                    .map(t -> t.attr("abs:href"))
                    .filter(StringUtils::isNotBlank)
                    .filter(t -> (! t.equals(url)))
                    .map(t -> get(t))
                    .findFirst().orElse(null);
                /*
                 * Heuristics to consider:
                 *
                 * Search for SPDX-License-Identifier
                 *
                 * If a single href of http://opensource.org/licenses/([^/])
                 * is found and $1 is in the LicenseMap
                 */
                /*
                 * Parse the document if the heuristics fail.
                 */
                if (value == null) {
                    value = parser.parse(resolver, url, document);
                }
            }
        } catch (FileNotFoundException exception) {
            log.debug("File not found: " + url);
        } catch (Exception exception) {
            log.warn("Cannot read " + url);
            log.debug(exception.getMessage());
        }

        if (value instanceof ExtractedLicenseInfo) {
            TextLicenseInfo.addSeeAlso((ExtractedLicenseInfo) value, url);
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
        String redirectURL = null;

        if (connection instanceof HttpURLConnection) {
            String url = connection.getURL().toString();
            int code = ((HttpURLConnection) connection).getResponseCode();

            if (REDIRECT_CODES.contains(code)) {
                String location = connection.getHeaderField("Location");

                if (isNotBlank(location)) {
                    redirectURL =
                        URI.create(url).resolve(location).toASCIIString();
                }
            }

            if (isBlank(redirectURL)) {
                redirectURL =
                    redirects.entrySet()
                    .stream()
                    .filter(t -> t.getKey().matcher(url).matches())
                    .map(t -> t.getKey().matcher(url).replaceFirst(t.getValue()))
                    .findFirst().orElse(null);
            }
        }

        return redirectURL;
    }

    @NoArgsConstructor @ToString
    private static class HostnameVerifierImpl implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
