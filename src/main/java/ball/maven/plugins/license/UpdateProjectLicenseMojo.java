package ball.maven.plugins.license;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.spdx.compare.LicenseCompareHelper;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.ListedLicenses;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;

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
    @Parameter(required = false, readonly = true,
               property = "license.name",
               defaultValue = "${project.licenses[0].name}")
    @Getter
    private String name = null;

    @Parameter(required = false, readonly = true,
               property = "license.url",
               defaultValue = "${project.licenses[0].url}")
    @Getter
    private URL url = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (name == null && url == null) {
            throw new MojoFailureException("One of license 'name' or 'url' must be specified");
        }

        License license = null;

        if (name != null) {
            try {
                license =
                    (License) LicenseInfoFactory.parseSPDXLicenseString(name);
            } catch (Exception exception) {
                log.warn("Cannot find SPDX license for " + name);
            }
        }

        copy(url, getFile().toPath());

        if (license == null) {
            try {
                String[] urls =
                    Stream.of(url)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toArray(String[]::new);
                String text =
                    Files.lines(getFile().toPath(), UTF_8)
                    .collect(joining(LF, EMPTY, LF));
                ExtractedLicenseInfo extracted =
                    new ExtractedLicenseInfo(name, text, name, urls, null);
                String[] ids =
                    LicenseCompareHelper.matchingStandardLicenseIds(extracted.getExtractedText());

                if (ids != null) {
                    getLog().warn("Project license matches SPDX ID(s): "
                                  + Arrays.asList(ids));
                }
            } catch (Exception exception) {
                log.warn("Cannot analyze license: " + getFile(), exception);
            }
        }
    }
}
