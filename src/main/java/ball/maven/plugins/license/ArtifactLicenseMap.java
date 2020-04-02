package ball.maven.plugins.license;

import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.apache.maven.artifact.Artifact;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseSet;
import org.spdx.rdfparser.license.OrLaterOperator;
import org.spdx.rdfparser.license.WithExceptionOperator;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
public class ArtifactLicenseMap extends TreeMap<Artifact,AnyLicenseInfo> {
    private static final Pattern INCLUDE =
        Pattern.compile("(?i)^(.*/|)(LICENSE([.][^/]+)?|about.html)$");
    private static final Pattern EXCLUDE =
        Pattern.compile("(?i)^.*[.]class$");

    /** @serial */ private final ArtifactModelMap map;
    /** @serial */ private final LicenseResolver resolver;

    /**
     * Sole constructor.
     *
     * @param   map             The injected {@link ArtifactModelMap}.
     * @param   resolver        The injected {@link LicenseResolver}.
     */
    @Inject
    public ArtifactLicenseMap(ArtifactModelMap map, LicenseResolver resolver) {
        super(ArtifactModelMap.ORDER);

        this.map = Objects.requireNonNull(map);
        this.resolver = Objects.requireNonNull(resolver);
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
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
        URL url = getArtifactURL(artifact);
        /*
         * Licenses specified in the Artifact's POM
         */
        List<URLLicenseInfo> specified =
            Stream.of(map.get(artifact).getLicenses())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(t -> new URLLicenseInfo(t.getName(), resolve(url, t.getUrl())))
            .collect(toList());
        List<AnyLicenseInfo> parsed =
            specified.stream()
            .map(t -> resolver.parse(t))
            .collect(toList());
        /*
         * Licenses found in or specified by the Artifact
         */
        List<AnyLicenseInfo> scanned =
            scan(url).stream()
            .map(t -> new URLLicenseInfo(t, resolve(url, t)))
            .map(t -> resolver.parse(t))
            .filter(t -> (! (t instanceof URLLicenseInfo)))
            .collect(toList());
        Map<String,AnyLicenseInfo> found =
            scanned.stream()
            .filter(t -> LicenseUtilityMethods.countOf(t) > 0)
            .filter(resolver::isFullySpdxListed)
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
            licenses.addAll(specified);

            licenses =
                IntStream.range(0, parsed.size())
                .mapToObj(t -> Arrays.asList(specified.get(t), parsed.get(t)))
                .peek(t -> t.sort(sieve(resolver)))
                .map(t -> t.get(0))
                .collect(toList());
        }

        AnyLicenseInfo license = resolver.toLicense(licenses);

        if ((licenses.isEmpty()
             && (! (specified.isEmpty() && scanned.isEmpty())))
            || ((! licenses.isEmpty()) && (! resolver.isFullySpdxListed(license)))) {
            log.debug("------------------------------------------------------------");
            log.debug(String.valueOf(url));
            log.debug("  Specified: " + specified);
            log.debug("  Parsed: " + parsed);
            log.debug("  Scanned: " + scanned);
            log.debug("  Found: " + found.values());
            log.debug("  License: " + license);
            log.debug("------------------------------------------------------------");
        }
        return license;
    }

    private URL getArtifactURL(Artifact artifact) {
        URL url = null;

        try {
            url =
                new URI("jar",
                        artifact.getFile().toURI().toASCIIString() + "!/",
                        null)
                .toURL();
        } catch (Exception exception) {
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
            log.debug(exception.getMessage() /* , exception */);
        }

        return isAbsolute;
    }

    private Set<String> scan(URL url) {
        Set<String> set = new TreeSet<>();

        try (JarFile jar =
                 ((JarURLConnection) url.openConnection()).getJarFile()) {
            Manifest manifest = jar.getManifest();

            if (manifest != null) {
                Stream.of("Bundle-License")
                    .map(t -> manifest.getMainAttributes().getValue(t))
                    .filter(t -> isNotBlank(t))
                    .forEach(t -> set.add(t));
            }

            jar.stream()
                .map(JarEntry::getName)
                .filter(t -> INCLUDE.matcher(t).matches())
                .filter(t -> (! EXCLUDE.matcher(t).matches()))
                .forEach(t -> set.add(t));
        } catch (Exception exception) {
        }

        return set;
    }

    private static final Comparator<? super Boolean> TRUTH_ORDER =
        (t, u) -> Objects.equals(t, u) ? 0 : (t ? -1 : 1);

    private Comparator<AnyLicenseInfo> sieve(LicenseResolver resolver) {
        Comparator<AnyLicenseInfo> comparator =
            Comparator
            .<AnyLicenseInfo,Boolean>comparing(t -> (! (t instanceof URLLicenseInfo)), TRUTH_ORDER)
            .thenComparing(t -> (! (t instanceof TextLicenseInfo)), TRUTH_ORDER)
            .thenComparing(t -> (! (t instanceof ExtractedLicenseInfo)), TRUTH_ORDER)
            .thenComparing(resolver::isFullySpdxListed, TRUTH_ORDER)
            .thenComparing(t -> (t instanceof LicenseSet), TRUTH_ORDER)
            .thenComparing(LicenseUtilityMethods::countOf,
                           Comparator.<Integer>reverseOrder())
            .thenComparing(t -> (t instanceof WithExceptionOperator), TRUTH_ORDER)
            .thenComparing(t -> (t instanceof OrLaterOperator), TRUTH_ORDER)
            .thenComparing(resolver::isPartiallySpdxListed, TRUTH_ORDER);

        return comparator;
    }

    private int countOf(Collection<? extends AnyLicenseInfo> collection) {
        return collection.stream().mapToInt(LicenseUtilityMethods::countOf).sum();
    }
}
