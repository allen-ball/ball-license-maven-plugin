package ball.maven.plugins.license;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.License;

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
public class ArtifactLicenseMap extends TreeMap<Artifact,License> {
    private final ArtifactProjectMap artifactProjectMap;
    private final URLLicenseMap urlLicenseMap;

    /**
     * Sole constructor.
     *
     * @param   artifactProjectMap
     *                          The injected {@link ArtifactProjectMap}.
     * @param   urlLicenseMap   The injected {@link URLLicenseMap}.
     */
    @Inject
    public ArtifactLicenseMap(ArtifactProjectMap artifactProjectMap,
                              URLLicenseMap urlLicenseMap) {
        super(Comparator.comparing(Artifact::getId));

        this.artifactProjectMap = Objects.requireNonNull(artifactProjectMap);
        this.urlLicenseMap = Objects.requireNonNull(urlLicenseMap);
    }

    @Override
    public License get(Object key) {
        License value = super.get(key);

        if (value == null) {
            value = compute((Artifact) key);

            put((Artifact) key, value);
        }

        return value;
    }

    private License compute(Artifact artifact) {
        License license = null;
        MavenProject project = artifactProjectMap.get(artifact);

        if (license == null) {
            if (project != null) {
                license = compute(project.getLicenses());
            }
        }

        if (license == null) {
            /*
             * TBD: Scan the Artifact
             */
        }

        return license;
    }

    private License compute(List<org.apache.maven.model.License> list) {
        License license = null;

        if (list != null) {
            for (org.apache.maven.model.License element : list) {
                AnyLicenseInfo info = null;

                if (info == null) {
                    try {
                        info = parseSPDXLicenseString(element.getName());
                    } catch (Exception exception) {
                    }
                }

                if (info == null) {
                    if (element.getUrl() != null) {
                        info = urlLicenseMap.get(element.getUrl());
                    }
                }

                if (info instanceof License) {
                    license = (License) info;
                }

                if (license != null) {
                    break;
                }
            }
        }

        return license;
    }
}
