package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import com.fasterxml.jackson.databind.JsonNode;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
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
import org.apache.http.entity.ContentType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.LicenseInfoFactory;

import static ball.maven.plugins.license.LicenseUtilityMethods.isFullySpdxListed;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;

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
    /** @serial */ private final Map<Pattern,String> redirects;

    /**
     * Sole constructor.
     *
     * @param   map             The injected {@link LicenseMap}.
     */
    @Inject
    public URLLicenseInfoParser(LicenseMap map) {
        super(String.CASE_INSENSITIVE_ORDER);

        this.map = Objects.requireNonNull(map);

        try {
            for (License value : map.values()) {
                String id = value.getLicenseId();

                put(String.format("https://opensource.org/licenses/%s", id),
                    value);
                put(String.format("https://spdx.org/licenses/%s.html", id),
                    value);
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
                AnyLicenseInfo value =
                    LicenseInfoFactory.parseSPDXLicenseString(id);

                if (! isFullySpdxListed(value)) {
                    throw new IllegalArgumentException(id);
                }

                Stream.of(seeds.getProperty(id).split("[\\p{Space}]+"))
                    .filter(StringUtils::isNotBlank)
                    .forEach(t -> putIfAbsent(t.trim(), value));
            }

            for (License value : map.values()) {
                for (String key : value.getSeeAlso()) {
                    if (isNotBlank(key)) {
                        putIfAbsent(key.trim(), value);
                    }
                }
            }

            Set<String> set =
                keySet().stream()
                .filter(t -> t.startsWith("https:"))
                .collect(toSet());

            set.stream()
                .forEach(t -> computeIfAbsent(t.replace("https:", "http:"),
                                              k -> get(t)));

            redirects =
                getXMLProperties("redirects").entrySet()
                .stream()
                .collect(toMap(k -> Pattern.compile(k.getKey().toString()),
                               v -> v.getValue().toString().trim()));
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ExceptionInInitializerError(exception);
        }
    }

    private Properties getXMLProperties(String name) throws Exception {
        Properties properties = new Properties();
        String resource =
            String.format("%1$s.%2$s.xml", getClass().getSimpleName(), name);

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
        URLConnection connection = null;

        try {
            connection = new URL(url).openConnection();

            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection)
                    .setInstanceFollowRedirects(false);
            }

            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier(NONE);
            }

            String canonicalURL = getCanonicalURL(connection);

            if (isNotBlank(canonicalURL) && (! equals(canonicalURL, url))) {
                if (value == null) {
                    value = get(canonicalURL);
                }
            }

            String redirectURL = getRedirectURL(connection);

            if (isNotBlank(redirectURL)) {
                if (! equals(redirectURL, url)) {
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
                ContentType type =
                    ContentType.parse(connection.getContentType());
                Charset charset =
                    (type.getCharset() != null) ? type.getCharset() : UTF_8;

                if (type.getMimeType().matches("(?i).*(html|xml).*")) {
                    try (InputStream in = connection.getInputStream()) {
                        Document document =
                            Jsoup.parse(in, charset.name(), url);

                        document.outputSettings()
                            .syntax(Document.OutputSettings.Syntax.xml);
                        /*
                         * Heuristic: Look for a "canonical" <link/> with a
                         * known href.
                         */
                        if (value == null) {
                            value =
                                document.select("head>link[rel='canonical'][href]")
                                .stream()
                                .map(t -> t.attr("abs:href"))
                                .filter(StringUtils::isNotBlank)
                                .map(t -> get(t))
                                .filter(Objects::nonNull)
                                .findFirst().orElse(null);
                        }
                        /*
                         * Heuristics to consider:
                         *
                         * Search for SPDX-License-Identifier
                         *
                         * If a single href of
                         * http://opensource.org/licenses/([^/]) is found
                         * and $1 is in the LicenseMap
                         */
                        /*
                         * Parse the document if the heuristics fail.
                         */
                        if (value == null) {
                            value =
                                Stream.of("content, .content, #content",
                                          "main, .main, #main",
                                          "body, .body, #body")
                                .map(t -> document.select(t))
                                .flatMap(Elements::stream)
                                .filter(Element::hasText)
                                .map(t -> t.text())
                                .distinct()
                                .map(t -> new TextLicenseInfo(url, t, url))
                                .map(t -> resolver.parse(t))
                                .filter(Objects::nonNull)
                                .filter(t -> (! (t instanceof ExtractedLicenseInfo)))
                                .findFirst()
                                .orElse(null);
                        }

                        if (value == null) {
                            value =
                                new TextLicenseInfo(url,
                                                    document.wholeText(), url);
                            value = resolver.parse(value);
                        }
                    }
                } else {
                    try (InputStream in = connection.getInputStream();
                         Scanner scanner = new Scanner(in, charset.name())) {
                        String text = scanner.useDelimiter("\\A").next();

                        value = new TextLicenseInfo(url, text, url);
                        value = resolver.parse(value);
                    }
                }
            }
        } catch (FileNotFoundException exception) {
            log.debug("File not found: " + url);
        } catch (Exception exception) {
            log.warn("Cannot read " + url);

            if (connection != null) {
                connection.getHeaderFields().entrySet().stream()
                    .forEach(t -> log.debug(String.valueOf(t)));
            }
        } finally {
            if (value == null) {
                value = new TextLicenseInfo(url, EMPTY, url);
            }
        }

        if (value instanceof ExtractedLicenseInfo) {
            TextLicenseInfo.addSeeAlso((ExtractedLicenseInfo) value, url);
        }

        return value;
    }

    private boolean equals(String left, String right) {
        return (Objects.compare(left, right, comparator()) == 0
                && Objects.compare(right, left, comparator()) == 0);
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
                    .filter(StringUtils::isNotBlank)
                    .map(t -> resolve((HttpURLConnection) connection, t))
                    .map(t -> t.toASCIIString())
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
                        resolve((HttpURLConnection) connection, location)
                        .toASCIIString();
                }
            }

            if (isBlank(redirectURL)) {
                for (Map.Entry<Pattern,String> entry : redirects.entrySet()) {
                    Matcher matcher = entry.getKey().matcher(url);

                    if (matcher.matches()) {
                        redirectURL = matcher.replaceFirst(entry.getValue());

                        if (isNotBlank(redirectURL)) {
                            break;
                        } else {
                            redirectURL = null;
                        }
                    }
                }
            }
        }

        return redirectURL;
    }

    private URI resolve(HttpURLConnection connection, String location) {
        URI uri = URI.create(location);

        if (! uri.isAbsolute()) {
            try {
                uri = connection.getURL().toURI().resolve(uri);
            } catch (Exception exception) {
                log.debug(exception.getMessage(), exception);
            }
        }

        return uri;
    }

    @NoArgsConstructor @ToString
    private static class HostnameVerifierImpl implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
