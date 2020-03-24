package ball.maven.plugins.license;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
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
import static org.apache.commons.lang3.StringUtils.isBlank;
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

    @Parameter(defaultValue = "false", property = "license.download")
    private boolean download = false;

    @Parameter(defaultValue = "true", property = "license.overwrite")
    private boolean overwrite = true;

    @Parameter(defaultValue = "true", property = "license.verify")
    private boolean verify = true;

    @Inject private MavenProject project = null;
    @Inject private ArtifactAnyLicenseInfoMap artifactAnyLicenseInfoMap = null;
    @Inject private URLAnyLicenseInfoMap urlAnyLicenseInfoMap = null;

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
                    license = (License) urlAnyLicenseInfoMap.get(url);
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
        if (download || (! getFile().exists())) {
            if (url != null) {
                copy(url, getFile().toPath(), overwrite);
            }
        }
        /*
         * ... verify any local copy is actually the specified license (and
         * fail if it is not), ...
         */
        if (verify) {
            if (license != null) {
                try {
                    String text =
                        Files.lines(getFile().toPath(), UTF_8)
                        .collect(joining(LF, EMPTY, LF));
                    boolean isDifferenceFound =
                        isTextStandardLicense(license, text)
                        .isDifferenceFound();

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
        }
        /*
         * ... update the project POM if necessary, ...
         */
        if (license != null) {
            File pom = project.getFile();
            Model model = null;

            try (FileInputStream in = new FileInputStream(pom)) {
                model = new MavenXpp3Reader().read(in);
            } catch (Exception exception) {
                fail("Cannot read " + pom, exception);
            }

            if (isBlank(model.getInceptionYear())
                || (model.getLicenses() == null
                    || model.getLicenses().size() != 1)
                || (! license.getLicenseId().equals(name))) {
                /*
                 * org.apache.maven.model.License update =
                 *     project.getLicenses().get(0).clone();
                 *
                 * update.setName(license.getLicenseId());
                 *
                 * model.setLicenses(Arrays.asList(update));
                 *
                 * File backup =
                 *     new File(pom.getParentFile(), pom.getName() + ".bak");
                 *
                 * pom.renameTo(backup);
                 *
                 * try (FileOutputStream out = new FileOutputStream(pom)) {
                 *     new MavenXpp3Writer().write(out, model);
                 * } catch (Exception exception) {
                 *     fail("Cannot write " + pom, exception);
                 * }
                 */
            }
        }
        /*
         * ... and cache the result.
         */
        if (license != null) {
            artifactAnyLicenseInfoMap.put(project.getArtifact(), license);
        }
    }
}
