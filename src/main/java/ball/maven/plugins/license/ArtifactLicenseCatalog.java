package ball.maven.plugins.license;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.execution.MavenSession;
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
import static org.spdx.rdfparser.license.LicenseInfoFactory.parseSPDXLicenseString;

/**
 * {@link Artifact} to {@link LicenseSet}
 * ({@link org.spdx.rdfparser.license.License}) {@link java.util.Map}
 * implementation.  The {@link #get(Object)} method transparently calculates
 * and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class ArtifactLicenseCatalog extends TreeMap<Artifact,AnyLicenseInfo> {
    private static final String CATALOG = "artifact-license-catalog.xml";

    private static final Pattern INCLUDE =
        Pattern.compile("(?i)^(.*/|)(LICENSE([.][^/]+)?|about.html)$");
    private static final Pattern EXCLUDE =
        Pattern.compile("(?i)^.*[.]class$");

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
        .thenComparing(LicenseUtilityMethods::countOf,
                       Comparator.<Integer>reverseOrder())
        .thenComparing(t -> (t instanceof WithExceptionOperator), TRUTH)
        .thenComparing(t -> (t instanceof OrLaterOperator), TRUTH)
        .thenComparing(LicenseUtilityMethods::isPartiallySpdxListed, TRUTH);

    /** @serial */ private final MavenSession session;
    /** @serial */ private final ArtifactModelCache cache;
    /** @serial */ private final LicenseResolver resolver;
    private final File file;
    private final Properties catalog = new Properties();

    /**
     * Sole constructor.
     *
     * @param   session         The injected {@link MavenSession}.
     * @param   cache           The injected {@link ArtifactModelCache}.
     * @param   resolver        The injected {@link LicenseResolver}.
     */
    @Inject
    public ArtifactLicenseCatalog(MavenSession session,
                                  ArtifactModelCache cache,
                                  LicenseResolver resolver) {
        super();

        this.session = Objects.requireNonNull(session);
        this.cache = Objects.requireNonNull(cache);
        this.resolver = Objects.requireNonNull(resolver);
        this.file =
            new File(session.getLocalRepository().getBasedir(), CATALOG);
    }

    @PostConstruct
    public void init() {
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                catalog.loadFromXML(in);
            } catch (IOException exception) {
                log.warn("Cannot read " + file);
            }

            for (String key : catalog.stringPropertyNames()) {
                try {
                    String[] gav = key.split(":", 3);
                    Artifact artifact =
                        new DefaultArtifact(gav[0], gav[1], gav[2],
                                            EMPTY, "pom", EMPTY, null);
                    AnyLicenseInfo license =
                        parseSPDXLicenseString(catalog.getProperty(key));

                    put(artifact, license);
                } catch (Exception exception) {
                    log.warn(exception.getMessage(), exception);
                }
            }
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());

        boolean changed =
            entrySet().stream()
            .filter(t -> isFullySpdxListed(t.getValue()))
            .map(t -> (! Objects.equals(t.getValue().toString(),
                                        catalog.put(key(t.getKey()),
                                                    t.getValue().toString()))))
            .reduce(Boolean::logicalOr).orElse(! file.exists());

        if (changed) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                catalog.storeToXML(out, file.getName());
            } catch (IOException exception) {
                log.warn("Cannot write " + file);
            }
        }
    }

    @Override
    public AnyLicenseInfo get(Object key) {
        AnyLicenseInfo value = super.get(key);

        if (value == null) {
            value = compute((Artifact) key);

            put((Artifact) key, value);
        }

        return value;
    }

    private AnyLicenseInfo compute(Artifact artifact) {
        URL url = toURL(artifact);
        /*
         * Licenses specified in the Artifact's POM
         */
        List<URLLicenseInfo> specified =
            Stream.of(cache.get(artifact).getLicenses())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(t -> new URLLicenseInfo(t.getName(),
                                         resolve(url, t.getUrl())))
            .collect(toList());
        /*
         * Licenses found in or specified by the Artifact
         */
        List<URLLicenseInfo> bundle = Collections.emptyList();
        List<AnyLicenseInfo> scanned = Collections.emptyList();

        try (JarFile jar =
                 ((JarURLConnection) url.openConnection()).getJarFile()) {
            Manifest manifest = jar.getManifest();

            if (manifest != null) {
                bundle =
                    Stream.of("Bundle-License")
                    .map(t -> manifest.getMainAttributes().getValue(t))
                    .filter(StringUtils::isNotBlank)
                    .flatMap(t -> Stream.of(t.split("[,\\p{Space}]+")))
                    .map(t -> Pattern.compile("((.+);link=)?(.*)").matcher(t))
                    .filter(t -> t.matches())
                    .map(t -> new URLLicenseInfo(isNotBlank(t.group(2)) ? t.group(2) : t.group(3),
                                                 resolve(url, t.group(3))))
                    .collect(toList());
            }

            Stream<URLLicenseInfo> scan =
                jar.stream()
                .map(JarEntry::getName)
                .filter(t -> INCLUDE.matcher(t).matches())
                .filter(t -> (! EXCLUDE.matcher(t).matches()))
                .map(t -> new URLLicenseInfo(t, resolve(url, t)));

            scanned =
                Stream.concat(bundle.stream(), scan)
                .map(t -> resolver.parse(t))
                .filter(t -> (! (t instanceof URLLicenseInfo)))
                .collect(toList());
        } catch (IOException exception) {
            log.debug(exception.getMessage() /* , exception */);
        }

        if (specified.isEmpty()) {
            specified.addAll(bundle);
        }

        List<AnyLicenseInfo> parsed =
            specified.stream()
            .map(t -> resolver.parse(t))
            .collect(toList());
        Map<String,AnyLicenseInfo> found =
            scanned.stream()
            .filter(t -> LicenseUtilityMethods.countOf(t) > 0)
            .filter(LicenseUtilityMethods::isFullySpdxListed)
            .collect(toMap(k -> k.toString(), v -> v, (t, u) -> t));

        if (specified.isEmpty() && found.isEmpty()) {
            log.warn(artifact + ": No license(s) specified or found");
        }

        List<AnyLicenseInfo> licenses = specified.stream().collect(toList());

        if (found.size() >= specified.size()
            || countOf(found.values()) == countOf(specified)) {
            licenses.clear();
            licenses.addAll(found.values());
        } else {
            licenses.clear();

            licenses =
                IntStream.range(0, parsed.size())
                .mapToObj(t -> Arrays.asList(specified.get(t), parsed.get(t)))
                .peek(t -> t.sort(SIEVE))
                .map(t -> t.get(0))
                .collect(toList());
        }

        AnyLicenseInfo license = resolver.toLicense(licenses);

        if ((licenses.isEmpty()
             && (! (specified.isEmpty() && scanned.isEmpty())))
            || ((! licenses.isEmpty()) && (! isFullySpdxListed(license)))) {
            log.debug("------------------------------------------------------------");
            log.debug(String.valueOf(url));
            log.debug("  POM:     " + specified);
            log.debug("  Bundle:  " + bundle);
            log.debug("  Parsed:  " + parsed);
            log.debug("  Scanned: " + scanned);
            log.debug("  Found:   " + found.values());
            log.debug("  License: " + license);
            log.debug("------------------------------------------------------------");
        }
        return license;
    }

    private URL toURL(Artifact artifact) {
        URL url = null;

        try {
            url =
                new URI("jar",
                        artifact.getFile().toURI().toASCIIString() + "!/",
                        null)
                .toURL();
        } catch(URISyntaxException | MalformedURLException exception) {
            log.debug(exception.getMessage(), exception);
            throw new IllegalStateException(exception);
        }

        return url;
    }

    private String[] resolve(URL root, String urls) {
        ArrayList<String> list = new ArrayList<>();

        if (isNotBlank(urls)) {
            for (String url : urls.split("[,\\p{Space}]+")) {
                if (isNotBlank(url)) {
                    String value = url;

                    if (! isAbsolute(value)) {
                        value = root.toString() + value;
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

    private int countOf(Collection<? extends AnyLicenseInfo> collection) {
        return collection.stream().mapToInt(LicenseUtilityMethods::countOf).sum();
    }
}
