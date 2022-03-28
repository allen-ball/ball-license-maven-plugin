package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * %%
 * Copyright (C) 2020 - 2022 Allen D. Ball
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseSet;
import org.spdx.rdfparser.license.OrLaterOperator;
import org.spdx.rdfparser.license.WithExceptionOperator;

import static ball.maven.plugins.license.LicenseUtilityMethods.isFullySpdxListed;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.maven.artifact.ArtifactUtils.key;

/**
 * {@link Artifact} to {@link LicenseSet}
 * ({@link org.spdx.rdfparser.license.License}) {@link java.util.Map}
 * implementation.  The {@link #get(Object)} method transparently calculates
 * and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@Named @Singleton
@Slf4j
public class ArtifactLicenseCatalog extends TreeMap<Artifact,AnyLicenseInfo> {
    private static final long serialVersionUID = -7887839577334232433L;

    private static final String CATALOG = "artifact-license-catalog.xml";

    private static final int FLUSH_PERIOD = 8;

    private static final Predicate<String> INCLUDE =
        Pattern.compile("(?i)^(.*/|)(LICENSE([.][^/]+)?|about.html)$")
        .asPredicate();
    private static final Predicate<String> EXCLUDE =
        Pattern.compile("(?i)^.*[.]class$")
        .asPredicate().negate();

    private static final Comparator<? super Boolean> TRUTH =
        (t, u) -> Objects.equals(t, u) ? 0 : (t ? -1 : 1);
    private static Comparator<? super AnyLicenseInfo> SIEVE =
        Comparator
        .<AnyLicenseInfo,Boolean>
        comparing(t -> (! (t instanceof URLLicenseInfo)), TRUTH)
        .thenComparing(t -> (! (t instanceof TextLicenseInfo)), TRUTH)
        .thenComparing(t -> (! (t instanceof ExtractedLicenseInfo)), TRUTH)
        .thenComparing(LicenseUtilityMethods::isFullySpdxListed, TRUTH)
        .thenComparing(t -> (t instanceof LicenseSet), TRUTH)
        .thenComparing(LicenseUtilityMethods::countOf, Comparator.<Integer>reverseOrder())
        .thenComparing(t -> (t instanceof WithExceptionOperator), TRUTH)
        .thenComparing(t -> (t instanceof OrLaterOperator), TRUTH)
        .thenComparing(LicenseUtilityMethods::isPartiallySpdxListed, TRUTH);

    /** @serial */ private final MavenSession session;
    /** @serial */ private final ArtifactModelCache cache;
    /** @serial */ private final LicenseMap map;
    /** @serial */ private final LicenseResolver resolver;
    /** @serial */ private final File file;
    /** @serial */ private final Properties defaults = new Properties();
    /** @serial */ private final Properties catalog = new Properties(defaults);

    /**
     * Sole constructor.
     *
     * @param   session         The injected {@link MavenSession}.
     * @param   cache           The injected {@link ArtifactModelCache}.
     * @param   map             The injected {@link LicenseMap}.
     * @param   resolver        The injected {@link LicenseResolver}.
     */
    @Inject
    public ArtifactLicenseCatalog(MavenSession session, ArtifactModelCache cache, LicenseMap map, LicenseResolver resolver) {
        super(Comparator.comparing(ArtifactUtils::key, String.CASE_INSENSITIVE_ORDER));

        this.session = Objects.requireNonNull(session);
        this.cache = Objects.requireNonNull(cache);
        this.map = Objects.requireNonNull(map);
        this.resolver = Objects.requireNonNull(resolver);
        this.file = new File(session.getLocalRepository().getBasedir(), CATALOG);
    }

    protected void load() {
        try (InputStream in = getClass().getResourceAsStream(CATALOG)) {
            if (in != null) {
                defaults.loadFromXML(in);
            }
        } catch (IOException exception) {
        }

        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                catalog.loadFromXML(in);
            } catch (IOException exception) {
                log.error("Cannot read {}", file);
            }
        }

        for (String key : catalog.stringPropertyNames()) {
            try {
                put(new KeyArtifact(key), resolver.parseLicenseString(catalog.getProperty(key)));
            } catch (Exception exception) {
                log.error("{}: {}", key, exception.getMessage(), exception);
            }
        }
    }

    protected void flush() {
        boolean dirty = (! file.exists());

        for (Map.Entry<Artifact,AnyLicenseInfo> entry : entrySet()) {
            AnyLicenseInfo license = entry.getValue();

            if (isFullySpdxListed(license)) {
                String key = ArtifactUtils.key(entry.getKey());
                String value = license.toString();

                dirty |= (! Objects.equals(value, catalog.put(key, value)));
            }
        }

        if (dirty) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                catalog.storeToXML(out, file.getName());
            } catch (IOException exception) {
                log.warn("Cannot write {}", file);
            }
        }
    }

    @PostConstruct
    public void init() {
        load();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> flush()));
    }

    @PreDestroy
    public void destroy() {
        flush();
        log.debug("{}.size() = {}", getClass().getSimpleName(), size());
    }

    @Override
    public AnyLicenseInfo get(Object key) {
        AnyLicenseInfo value = super.get(key);

        if (value == null) {
            value = compute((Artifact) key);

            put((Artifact) key, value);

            if ((size() % FLUSH_PERIOD) == 0) {
                flush();
            }
        }

        return value;
    }

    private AnyLicenseInfo compute(Artifact artifact) {
        URL url = toURL(artifact);
        /*
         * Licenses specified in the Manifest Bundle-License
         * or found in the Artifact
         */
        List<AnyLicenseInfo> bundle = Collections.emptyList();
        List<AnyLicenseInfo> scanned = Collections.emptyList();

        try {
            JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile();
            Pattern pattern = Pattern.compile("((?<id>.+);link=)?(?<url>.*)");

            bundle =
                Stream.of(jar.getManifest())
                .filter(Objects::nonNull)
                .map(t -> t.getMainAttributes().getValue("Bundle-License"))
                .filter(StringUtils::isNotBlank)
                .flatMap(t -> Stream.of(t.split(",")))
                .map(t -> t.trim())
                .distinct()
                .map(pattern::matcher)
                .filter(Matcher::matches)
                .map(t -> parse(t.group("id"), resolve(url, t.group("url"))))
                .collect(toList());

            scanned =
                jar.stream()
                .map(JarEntry::getName)
                .filter(INCLUDE)
                .filter(EXCLUDE)
                .map(t -> parse(t, resolve(url, t)))
                .filter(t -> (! (t instanceof URLLicenseInfo)))
                .collect(toList());
        } catch (ZipException exception) {
        } catch (IOException exception) {
            log.debug("{}: {}", artifact, exception.getMessage(), exception);
        }

        Map<String,AnyLicenseInfo> found =
            scanned.stream()
            .filter(t -> LicenseUtilityMethods.countOf(t) > 0)
            .filter(LicenseUtilityMethods::isFullySpdxListed)
            .collect(toMap(k -> k.toString(), v -> v, (t, u) -> t));
        /*
         * Licenses specified in the Artifact's POM
         */
        List<AnyLicenseInfo> pom = Collections.emptyList();
        Model model = cache.get(artifact);

        if (model != null) {
            pom =
                Stream.of(model.getLicenses())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .map(t -> parse((t.getName() != null) ? t.getName().replaceAll(",", "") : null,
                                resolve(toURL(model.getUrl()), t.getUrl())))
                .collect(toList());
        }

        if (bundle.isEmpty() && pom.isEmpty() && found.isEmpty()) {
            log.warn("{}: No license(s) specified or found", artifact);
        }

        List<AnyLicenseInfo> licenses = bundle.stream().collect(toList());

        if (! isFullySpecified(licenses)) {
            if (licenses.isEmpty() || isFullySpecified(pom)) {
                licenses.clear();
                licenses.addAll(pom);
            }
        }

        if ((! isFullySpecified(licenses)) && found.size() >= licenses.size()) {
            licenses.clear();
            licenses.addAll(found.values());
        }

        if ((! licenses.isEmpty()) && (! isFullySpecified(licenses))) {
            log.debug("------------------------------------------------------------");
            log.debug("{}", ArtifactUtils.key(artifact));
            log.debug("{}", url);
            log.debug("      Bundle: {}", bundle);
            log.debug("         POM: {}", pom);
            log.debug("     Scanned: {}", scanned);
            log.debug("       Found: {}", found.values());
            log.debug("  License(s): {}", licenses);
            log.debug("------------------------------------------------------------");
        }

        return resolver.toLicense(licenses);
    }

    private URL toURL(Artifact artifact) {
        URL url = null;

        try {
            url = new URI("jar", artifact.getFile().toURI().toASCIIString() + "!/", null).toURL();
        } catch(URISyntaxException | MalformedURLException exception) {
            log.debug("{}: {}", artifact, exception.getMessage(), exception);
            throw new IllegalStateException(exception);
        }

        return url;
    }

    private URL toURL(String string) {
        URL url = null;

        if (isNotBlank(string)) {
            try {
                url = new URI(string).toURL();
            } catch(URISyntaxException | MalformedURLException exception) {
                log.debug("{}: {}", string, exception.getMessage(), exception);
            }
        }

        return url;
    }

    private String[] resolve(URL root, String urls) {
        ArrayList<String> list = new ArrayList<>();

        if (isNotBlank(urls)) {
            for (String url : urls.split("[,\\p{Space}]+")) {
                if (isNotBlank(url)) {
                    String value = url;

                    if (root != null && (! isAbsolute(value))) {
                        String string = root.toString();

                        if (! string.endsWith("/")) {
                            string += "/";
                        }

                        value = string + value;
                    }

                    list.add(value);
                }
            }
        }

        return list.toArray(new String[] { });
    }

    private boolean isAbsolute(String url) {
        boolean isAbsolute = false;

        try {
            isAbsolute = new URI(url).isAbsolute();
        } catch (Exception exception) {
        }

        return isAbsolute;
    }

    private AnyLicenseInfo parse(String id, String... urls) {
        AnyLicenseInfo option0 = isNotBlank(id) ? map.get(id) : null;
        AnyLicenseInfo option1 = null;

        try {
            option1 = isNotBlank(id) ? resolver.parseLicenseString(id) : null;
        } catch (Exception exception) {
        }

        AnyLicenseInfo option2 = new URLLicenseInfo(isNotBlank(id) ? id : urls[0], urls);
        AnyLicenseInfo[] options =
            Stream.of(option0, option1, option2)
            .filter(Objects::nonNull)
            .map(t -> resolver.parse(t))
            .toArray(AnyLicenseInfo[]::new);

        Arrays.sort(options, SIEVE);

        return (options.length > 0) ? options[0] : null;
    }

    private int countOf(Collection<? extends AnyLicenseInfo> collection) {
        return collection.stream().mapToInt(LicenseUtilityMethods::countOf).sum();
    }

    private boolean isFullySpecified(Collection<AnyLicenseInfo> collection) {
        return ((! collection.isEmpty()) && collection.stream().allMatch(t -> isFullySpdxListed(t)));
    }

    private class KeyArtifact extends DefaultArtifact {
        public KeyArtifact(String gav) { this(gav.split(":", 3)); }

        private KeyArtifact(String[] gav) {
            super(gav[0], gav[1], gav[2], EMPTY, "pom", EMPTY, null);
        }
    }
}
