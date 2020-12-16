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
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ConjunctiveLicenseSet;
import org.spdx.rdfparser.license.LicenseSet;
import org.spdx.rdfparser.license.OrLaterOperator;
import org.spdx.rdfparser.license.WithExceptionOperator;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate LICENSE and DEPENDENCIES
 * resources.
 *
 * {@maven.plugin.fields}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-license-resources",
      configurator = "license-mojo-component-configurator",
      requiresDependencyResolution = TEST,
      defaultPhase = GENERATE_RESOURCES, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateLicenseResourcesMojo extends AbstractLicenseMojo {
    private static final String DEPENDENCIES = "DEPENDENCIES";

    private static final String COMPILE = "compile";
    private static final String PROVIDED = "provided";
    private static final String RUNTIME = "runtime";
    private static final String SYSTEM = "system";
    private static final String TEST = "test";
    private static final String INCLUDE_SCOPE = COMPILE + "," + RUNTIME;
    private static final String EXCLUDE_SCOPE = EMPTY;

    private static final TreeSet<String> ARCHIVE_PACKAGING =
        Stream.of("jar", "maven-plugin", "ejb", "war", "ear", "rar")
        .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

    private static final Comparator<AnyLicenseInfo> LICENSE_ORDER =
        Comparator
        .<AnyLicenseInfo>comparingInt(t -> LicenseUtilityMethods.countOf(t))
        .reversed()
        .thenComparing(t -> toString(t))
        .thenComparingInt(t -> (t instanceof OrLaterOperator) ? -1 : 1)
        .thenComparingInt(t -> (t instanceof WithExceptionOperator) ? -1 : 1);
    private static final Comparator<Model> MODEL_ORDER =
        Comparator
        .<Model,String>comparing(t -> Objects.toString(t.getName(), EMPTY))
        .thenComparing(t -> Objects.toString(t.getUrl(), EMPTY))
        .thenComparingInt(t -> isBlank(t.getName()) ? Objects.hashCode(t) : 0);

    @Parameter(defaultValue = "${project.build.outputDirectory}",
               property = "license.resources.directory")
    private File directory = null;

    @Parameter(defaultValue = "META-INF",
               property = "license.resources.subdirectory")
    private String subdirectory = null;

    @Parameter(defaultValue = INCLUDE_SCOPE, property = "license.includeScope")
    private String includeScope = INCLUDE_SCOPE;

    @Parameter(defaultValue = EXCLUDE_SCOPE, property = "license.excludeScope")
    private String excludeScope = EXCLUDE_SCOPE;

    @Parameter(required = false)
    private Selection[] selections = null;

    @Parameter(defaultValue = "true")
    private boolean skipIfEmpty = true;

    @Inject private MavenProject project = null;
    @Inject private ArtifactLicenseCatalog catalog = null;
    @Inject private ArtifactModelCache cache = null;
    @Inject private ExecutorServiceImpl executor = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (! isSkip()) {
                String packaging = project.getPackaging();

                if (ARCHIVE_PACKAGING.contains(packaging)) {
                    if (directory.exists() && (! directory.isDirectory())) {
                        throw new FileAlreadyExistsException(directory.toString(),
                                                             null,
                                                             "Is not a directory");
                    }

                    Path parent = directory.toPath();

                    if (subdirectory != null && (! subdirectory.isEmpty())) {
                        Path path = Paths.get(subdirectory);

                        if (path.isAbsolute()) {
                            throw new IllegalArgumentException(path + " is not relative");
                        }

                        parent = parent.resolve(path);
                    }
                    /*
                     * Populate the caches.
                     */
                    Selections selections = new Selections();
                    Set<String> scope = getScope();
                    List<Callable<AnyLicenseInfo>> tasks =
                        project.getArtifacts()
                        .stream()
                        .filter(t -> scope.contains(t.getScope()))
                        .<Callable<AnyLicenseInfo>>map(t -> (() -> selections.get(t)))
                        .collect(toList());

                    executor.invokeAll(tasks);
                    /*
                     * Get the records for the report.
                     */
                    List<Tuple> tuples =
                        project.getArtifacts()
                        .stream()
                        .filter(t -> scope.contains(t.getScope()))
                        .map(t -> new Tuple(selections.get(t), cache.get(t), t))
                        .collect(toList());
                    /*
                     * LICENSE
                     */
                    generateLicense(parent);
                    /*
                     * DEPENDENCIES
                     *
                     * Report sort order:
                     *      Licenses (LICENSE_ORDER)
                     *      Model[name, url] (same if name is not blank)
                     *      Artifacts (ArtifactModelCache.ORDER)
                     */
                    TreeMap<AnyLicenseInfo,Map<Model,List<Tuple>>> report =
                        tuples.stream()
                        .collect(groupingBy(Tuple::getLicense,
                                            () -> new TreeMap<>(LICENSE_ORDER),
                                            groupingBy(Tuple::getModel,
                                                       () -> new TreeMap<>(MODEL_ORDER),
                                                       toList())));

                    warnIfExtractedLicenseInfo(report.keySet().stream());

                    if ((! report.isEmpty()) || (! skipIfEmpty)) {
                        generateReport(report, parent);
                    } else {
                        log.info("Skipping empty {} resource generation",
                                 DEPENDENCIES);
                    }
                } else {
                    log.warn("Skipping for '{}' packaging", packaging);
                }
            } else {
                log.info("Skipping {} and {} resource generation",
                         getFile().getName(), DEPENDENCIES);
            }
        } catch (IOException exception) {
            fail(exception.getMessage(), exception);
        } catch (Throwable throwable) {
            log.error("{}", throwable.getMessage(), throwable);

            if (throwable instanceof MojoExecutionException) {
                throw (MojoExecutionException) throwable;
            } else if (throwable instanceof MojoFailureException) {
                throw (MojoFailureException) throwable;
            } else {
                throw new MojoExecutionException(throwable.getMessage(),
                                                 throwable);
            }
        } finally {
            catalog.flush();
        }
    }

    private Set<String> getScope() {
        Pattern pattern = Pattern.compile("[\\p{Punct}\\p{Space}]+");
        TreeSet<String> scope =
            pattern.splitAsStream(includeScope)
            .filter(StringUtils::isNotBlank)
            .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        pattern.splitAsStream(excludeScope)
            .filter(StringUtils::isNotBlank)
            .forEach(t -> scope.remove(t));

        if (scope.isEmpty()) {
            log.warn("Specified scope is empty");
            log.debug("    includeScope = {}", includeScope);
            log.debug("    excludeScope = {}", excludeScope);
        }

        return scope;
    }

    private void generateLicense(Path parent) throws Exception {
        File file = getFile();

        if (file.exists() && file.isDirectory()) {
            throw new FileAlreadyExistsException(file.toString(),
                                                 null,
                                                 "Is not a file or link");
        }

        Path source = file.toPath();
        Path target = parent.resolve(source.getFileName());

        Files.createDirectories(parent);
        Files.copy(file.toPath(), target, REPLACE_EXISTING);
        Files.setLastModifiedTime(target, Files.getLastModifiedTime(source));
    }

    private void generateReport(TreeMap<AnyLicenseInfo,Map<Model,List<Tuple>>> report,
                                Path parent) throws Exception {
        Path target = parent.resolve(DEPENDENCIES);

        try (PrintWriter out =
                 new PrintWriter(Files.newBufferedWriter(target,
                                                         CREATE, WRITE,
                                                         TRUNCATE_EXISTING))) {
            String boundary = String.join(EMPTY, Collections.nCopies(78, "-"));

            out.println(boundary);
            out.println(target.getFileName() + " " + project.getArtifact());
            out.println(boundary);

            for (Map.Entry<AnyLicenseInfo,Map<Model,List<Tuple>>> section :
                     report.entrySet()) {
                out.println(boundary);
                out.println(toString(section.getKey()));
                out.println(boundary);

                boolean first = true;

                for (Map.Entry<Model,List<Tuple>> group :
                         section.getValue().entrySet()) {
                    if (first) {
                        first = false;
                    } else {
                        out.println();
                    }

                    if (isNotBlank(group.getKey().getName())) {
                        out.println(group.getKey().getName());
                    }

                    for (Tuple tuple : group.getValue()) {
                        out.println(tuple.getArtifact());
                    }

                    if (isNotBlank(group.getKey().getUrl())) {
                        out.println(group.getKey().getUrl());
                    }
                }

                out.println(boundary);
            }
        }
    }

    private static String toString(AnyLicenseInfo license) {
        String string = null;

        if (license instanceof LicenseSet) {
            AnyLicenseInfo[] members = ((LicenseSet) license).getMembers();

            Arrays.sort(members, Comparator.comparing(Objects::toString));

            string =
                Stream.of(members)
                .map(Objects::toString)
                .collect(joining((license instanceof ConjunctiveLicenseSet) ? " AND " : " OR "));
        } else {
            string = Objects.toString(license, EMPTY);
        }

        return string;
    }

    /**
     * {@link Selection}s @{@link Parameter} {@link Map}.  Dispatches to
     * {@link #catalog} if no license is specified.
     */
    @NoArgsConstructor
    private class Selections extends TreeMap<String,AnyLicenseInfo> {
        private static final long serialVersionUID = 4229926081116865138L;

        {
            if (selections != null) {
                Stream.of(selections)
                    .forEach(t -> put(t.getKey(), t.getValue()));
            }
        }

        @Override
        public AnyLicenseInfo get(Object key) {
            AnyLicenseInfo value = null;

            if (key instanceof Artifact) {
                Artifact artifact = (Artifact) key;

                if (value == null) {
                    value = get(ArtifactUtils.key(artifact));
                }

                if (value == null) {
                    value = get(ArtifactUtils.versionlessKey(artifact));
                }

                if (value == null) {
                    value = catalog.get(artifact);
                }
            } else {
                value = super.get(key);
            }

            return value;
        }
    }

    @AllArgsConstructor @Getter @ToString
    private class Tuple {
        private AnyLicenseInfo license = null;
        private Model model = null;
        private Artifact artifact = null;
    }
}
