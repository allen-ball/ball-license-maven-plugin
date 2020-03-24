package ball.maven.plugins.license;

import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.SpdxNoneLicense;

import static java.util.stream.Collectors.toCollection;
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
public class ArtifactAnyLicenseInfoMap extends TreeMap<Artifact,AnyLicenseInfo> {
    private static final Pattern LICENSE =
        Pattern.compile("(?is)^(.*/|)(LICENSE|about.html)$");

    private final ArtifactProjectMap artifactProjectMap;
    private final URLAnyLicenseInfoMap urlAnyLicenseInfoMap;

    /**
     * Sole constructor.
     *
     * @param   artifactProjectMap
     *                          The injected {@link ArtifactProjectMap}.
     * @param   urlAnyLicenseInfoMap
     *                          The injected {@link URLAnyLicenseInfoMap}.
     */
    @Inject
    public ArtifactAnyLicenseInfoMap(ArtifactProjectMap artifactProjectMap,
                                     URLAnyLicenseInfoMap urlAnyLicenseInfoMap) {
        super(Comparator.comparing(Artifact::getId));

        this.artifactProjectMap =
            Objects.requireNonNull(artifactProjectMap);
        this.urlAnyLicenseInfoMap =
            Objects.requireNonNull(urlAnyLicenseInfoMap);
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
        License license = null;
        MavenProject project = artifactProjectMap.get(artifact);
        LinkedHashSet<String> names =
            project.getLicenses().stream()
            .map(t -> t.getName())
            .filter(StringUtils::isNotBlank)
            .collect(toCollection(LinkedHashSet::new));
        LinkedHashSet<String> urls =
            project.getLicenses().stream()
            .map(t -> t.getUrl())
            .filter(StringUtils::isNotBlank)
            .collect(toCollection(LinkedHashSet::new));

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
                        urls.add(value);
                    }
                }

                jar.stream()
                    .filter(t -> LICENSE.matcher(t.getName()).matches())
                    .map(t -> url.toString() + t)
                    .forEach(t -> urls.add(t));
            }
        } catch (MalformedURLException exception) {
            throw new IllegalStateException(exception);
        } catch (Exception exception) {
        }

        if (license == null) {
            for (String name : names) {
                try {
                    license = (License) parseSPDXLicenseString(name);

                    if (license != null) {
                        break;
                    }
                } catch (Exception exception) {
                }
            }
        }

        if (license == null) {
            for (String url : urls) {
                try {
                    license = (License) urlAnyLicenseInfoMap.get(url);

                    if (license != null) {
                        break;
                    }
                } catch (Exception exception) {
                }
            }
        }

        if (license == null) {
            if (names.isEmpty() && urls.isEmpty()) {
                log.warn(artifact + ": No license specified");
            } else {
                log.warn(artifact + ": Cannot find SPDX license");
                log.debug("        " + artifact.getFile());
                log.debug("        " + names);
                log.debug("        " + urls);
            }
        }

        AnyLicenseInfo value = license;

        if (value == null) {
            if (! urls.isEmpty()) {
                value = urlAnyLicenseInfoMap.get(urls.iterator().next());
            } else if (! names.isEmpty()) {
                value =
                    new ExtractedLicenseInfo(names.iterator().next(), null);
            } else {
                value = new SpdxNoneLicense();
            }
        }

        return value;
    }
}
