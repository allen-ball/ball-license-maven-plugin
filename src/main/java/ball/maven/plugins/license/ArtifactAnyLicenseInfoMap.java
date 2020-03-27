package ball.maven.plugins.license;

import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
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
import org.spdx.rdfparser.license.SimpleLicensingInfo;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {@link Artifact} to {@link AnyLicenseInfo}
 * ({@link org.spdx.rdfparser.license.License}) {@link java.util.Map}
 * implementation.  The {@link #get(Object)} method transparently calculates
 * and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class ArtifactAnyLicenseInfoMap
             extends TreeMap<Artifact,List<AnyLicenseInfo>> {
    private static final Pattern INCLUDE =
        Pattern.compile("(?i)^(.*/|)(LICENSE([.][^/]+)?|about.html)$");
    private static final Pattern EXCLUDE =
        Pattern.compile("(?i)^.*[.]class$");

    /** @serial */ private final ArtifactModelMap artifactModelMap;
    /** @serial */ private final URLAnyLicenseInfoMap urlAnyLicenseInfoMap;

    /**
     * Sole constructor.
     *
     * @param   artifactModelMap
     *                          The injected {@link ArtifactModelMap}.
     * @param   urlAnyLicenseInfoMap
     *                          The injected {@link URLAnyLicenseInfoMap}.
     */
    @Inject
    public ArtifactAnyLicenseInfoMap(ArtifactModelMap artifactModelMap,
                                     URLAnyLicenseInfoMap urlAnyLicenseInfoMap) {
        super(ArtifactModelMap.ORDER);

        this.artifactModelMap =
            Objects.requireNonNull(artifactModelMap);
        this.urlAnyLicenseInfoMap =
            Objects.requireNonNull(urlAnyLicenseInfoMap);
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    @Override
    public List<AnyLicenseInfo> get(Object key) {
        List<AnyLicenseInfo> value = super.get(key);

        if (value == null) {
            value = compute((Artifact) key);

            put((Artifact) key, value);
        }

        return value;
    }

    private List<AnyLicenseInfo> compute(Artifact artifact) {
        URL url = getArtifactURL(artifact);
        List<AnyLicenseInfo> specified =
            Stream.of(artifactModelMap.get(artifact).getLicenses())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(t -> urlAnyLicenseInfoMap
                      .parse(t.getName(), resolve(url, t.getUrl())))
            .collect(toList());
        Set<String> scan = scan(url);

        if (specified.isEmpty() && scan.isEmpty()) {
            log.warn(artifact + ": No license(s) specified or found");
        }

        Map<String,AnyLicenseInfo> scanned =
            scan.stream()
            .map(t -> urlAnyLicenseInfoMap.parse(null, t))
            .filter(t -> (t instanceof SimpleLicensingInfo))
            .map(t -> (SimpleLicensingInfo) t)
            .filter(t -> isNotBlank(t.getLicenseId()))
            .collect(toMap(k -> k.getLicenseId(), v -> v,
                           (v1, v2) -> v1, LinkedHashMap::new));

        if (specified.isEmpty()) {
            specified.addAll(scanned.values());
        } else {
            List<AnyLicenseInfo> list =
                scanned.values().stream().collect(toList());

            list.removeAll(specified);
            list.removeIf(t -> (t instanceof ExtractedLicenseInfo));

            for (int i = 0, n = specified.size(); i < n; i += 1) {
                if (! list.isEmpty()) {
                    if (specified.get(i) instanceof ExtractedLicenseInfo) {
                        specified.set(i, list.remove(0));
                    }
                }
            }
        }

        return specified;
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

    private String resolve(URL root, String url) {
        if (isNotBlank(url) && (! URI.create(url).isAbsolute())) {
            url = root.toString() + url;
        }

        return url;
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
                    .map(t -> resolve(url, t))
                    .forEach(t -> set.add(t));
            }

            jar.stream()
                .map(JarEntry::getName)
                .filter(t -> INCLUDE.matcher(t).matches())
                .filter(t -> (! EXCLUDE.matcher(t).matches()))
                .map(t -> resolve(url, t))
                .forEach(t -> set.add(t));
        } catch (Exception exception) {
        }

        return set;
    }
}
