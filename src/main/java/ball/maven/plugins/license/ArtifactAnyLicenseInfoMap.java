package ball.maven.plugins.license;

import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.SpdxNoneLicense;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.spdx.rdfparser.license.LicenseInfoFactory.parseSPDXLicenseString;

/**
 * {@link Artifact} to {@link License} {@link java.util.Map}
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
    private static final Pattern LICENSE =
        Pattern.compile("(?is)^(.*/|)(LICENSE|about.html)$");

    private final ArtifactModelMap artifactModelMap;
    private final URLAnyLicenseInfoMap urlAnyLicenseInfoMap;

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
        Model model = artifactModelMap.get(artifact);
        int expected =
            Math.max((model.getLicenses() != null) ? model.getLicenses().size() : 0, 1);
        List<AnyLicenseInfo> list =
            Stream.of(model.getLicenses())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .map(t -> urlAnyLicenseInfoMap.parse(t.getName(), t.getUrl()))
            .filter(Objects::nonNull)
            .collect(toList());
        Set<String> urls = scan(artifact);

        Stream.of(model.getLicenses())
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(t -> isNotBlank(t.getUrl()))
            .forEach(t -> urls.remove(t.getUrl()));

        list.removeIf(t -> t instanceof SpdxNoneLicense);
        /*
         * TBD -- Scan artifact for licenses.
         */
        if (list.isEmpty()) {
            if (model.getLicenses().isEmpty() && urls.isEmpty()) {
                log.warn(artifact + ": No license(s) specified");
            } else {
                log.warn(artifact + ": Cannot find SPDX license");
                log.debug("        " + artifact.getFile());
                log.debug("        " + urls);
            }
        }

        return list;
    }

    private Set<String> scan(Artifact artifact) {
        Set<String> set = new TreeSet<>();

        try {
            URL url =
                new URL("jar:file://"
                        + artifact.getFile().getAbsolutePath() + "!/");

            try (JarFile jar =
                     ((JarURLConnection) url.openConnection()).getJarFile()) {
                Manifest manifest = jar.getManifest();

                if (manifest != null) {
                    String value =
                        manifest.getMainAttributes()
                        .getValue("Bundle-License");

                    if (value != null) {
                        set.add(value);
                    }
                }

                jar.stream()
                    .filter(t -> LICENSE.matcher(t.getName()).matches())
                    .map(t -> url.toString() + t)
                    .forEach(t -> set.add(t));
            }
        } catch (MalformedURLException exception) {
            throw new IllegalStateException(exception);
        } catch (Exception exception) {
        }

        return set;
    }
}
