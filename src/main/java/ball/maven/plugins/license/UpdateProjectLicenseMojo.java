package ball.maven.plugins.license;

import java.nio.file.Files;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.license.License;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;
import static org.spdx.compare.LicenseCompareHelper.isTextStandardLicense;
import static org.spdx.rdfparser.license.LicenseInfoFactory.parseSPDXLicenseString;

/**
 * {@link org.apache.maven.plugin.Mojo} to update project LICENSE.
 *
 * {@maven.plugin.fields}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "update-project-license", defaultPhase = INITIALIZE, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class UpdateProjectLicenseMojo extends AbstractLicenseMojo {

    /**
     * {@link #SPDX_LICENSE_IDENTIFIER} = {@value #SPDX_LICENSE_IDENTIFIER}
     */
    protected static final String SPDX_LICENSE_IDENTIFIER =
        "SPDX-License-Identifier";

    @Parameter(defaultValue = "${project.licenses[0].name}",
               property = "license.name")
    private String name = null;

    @Parameter(defaultValue = "${project.licenses[0].url}",
               property = "license.url")
    private String url = null;

    @Inject private MavenProject project = null;
    @Inject private ArtifactLicenseMap artifactLicenseMap = null;
    @Inject private URLLicenseMap urlLicenseMap = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (name == null && url == null) {
            throw new MojoFailureException("One of license 'name' or 'url' must be specified");
        }
        /*
         * Find the license from name and/or URL, ...
         */
        License license = null;

        if (license == null) {
            if (name != null) {
                try {
                    license = (License) parseSPDXLicenseString(name);
                } catch (Exception exception) {
                    log.warn("Cannot find SPDX license for " + name);
                }
            }
        }

        if (license == null) {
            if (url != null) {
                try {
                    license = (License) urlLicenseMap.get(url);
                } catch (Exception exception) {
                    log.warn("Cannot find SPDX license for " + url);
                }
            }
        }

        if (license != null) {
            if (! license.getLicenseId().equals(name)) {
                log.warn(SPDX_LICENSE_IDENTIFIER
                         + ": " + license.getLicenseId());
            }
        }
        /*
         * ... update the project copy, ...
         */
        if (url != null) {
            copy(url, getFile().toPath());
        } else if (license != null) {
            try {
                Files.write(getFile().toPath(),
                            license.getStandardLicenseTemplate()
                            .getBytes(UTF_8));
            } catch (Exception exception) {
                fail("Cannot write " + getFile(), exception);
            }
        }
        /*
         * ... verify any pre-existing local copy is actually the specified
         * license, ...
         */
        if (license != null) {
            try {
                String text =
                    Files.lines(getFile().toPath(), UTF_8)
                    .collect(joining(LF, EMPTY, LF));
                boolean isDifferenceFound =
                    isTextStandardLicense(license, text).isDifferenceFound();

                if (isDifferenceFound) {
                    fail(getFile() + " does not contain "
                         + license.getLicenseId() + " license text");
                }
            } catch (MojoFailureException exception) {
                throw exception;
            } catch (Exception exception) {
                fail("Cannot analyze " + getFile(), exception);
            }
        }
        /*
         * ... update the project POM if necessary, ...
         */
        if (license != null) {
            /*
             * TBD
             */
        }
        /*
         * ... and cache the result.
         */
        if (license != null) {
            artifactLicenseMap.put(project.getArtifact(), license);
        }
    }
}
