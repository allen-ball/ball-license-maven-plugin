package ball.maven.plugins.license;

/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020 Allen D. Ball
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
/* import java.io.FileOutputStream; */
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
/* import org.apache.maven.model.io.xpp3.MavenXpp3Writer; */
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.License;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;
import static org.spdx.compare.LicenseCompareHelper.isTextStandardLicense;

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
    @Inject private ArtifactLicenseCatalog catalog = null;
    @Inject private LicenseResolver resolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (name == null && url == null) {
            throw new MojoFailureException("One of license 'name' or 'url' must be specified");
        }
        /*
         * Find the license from name and/or URL, ...
         */
        AnyLicenseInfo license = null;

        if (license == null) {
            List<AnyLicenseInfo> list =
                Stream.of(project.getLicenses())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(t -> new URLLicenseInfo(t.getName(), t.getUrl()))
                .map(t -> resolver.parse(t))
                .collect(toList());

            license = resolver.toLicense(list);

            if (! LicenseUtilityMethods.isFullySpdxListed(license)) {
                warnIfExtractedLicenseInfo(Stream.of(license));
            }
        }

        if (license != null) {
            if (LicenseUtilityMethods.isFullySpdxListed(license)) {
                String id = license.toString();

                if (id != null && (! id.equals(name))) {
                    log.warn(SPDX_LICENSE_IDENTIFIER + ": " + id);
                }
            } else {
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
            if (LicenseUtilityMethods.isFullySpdxListed(license)) {
                /*
                 * TBD: Bad assumption.
                 */
                if (license instanceof License) {
                    try {
                        Document document = Jsoup.parse(getFile(), null);

                        document.outputSettings()
                            .syntax(Document.OutputSettings.Syntax.xml);

                        boolean isDifferenceFound =
                            isTextStandardLicense((License) license,
                                                  document.body().text())
                            .isDifferenceFound();

                        if (isDifferenceFound) {
                            fail(getFile() + " does not contain "
                                 + ((License) license).getLicenseId()
                                 + " license text");
                        }
                    } catch (MojoFailureException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        fail("Cannot analyze " + getFile(), exception);
                    }
                }
            }
        }
        /*
         * ... update the project POM if necessary, ...
         */
        if (license instanceof License) {
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
                || (! ((License) license).getLicenseId().equals(name))) {
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
         * ... and record the result.
         */
        if (license != null) {
            catalog.put(project.getArtifact(), license);
            catalog.flush();
        }
    }
}
