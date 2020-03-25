package ball.maven.plugins.license;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.license.AnyLicenseInfo;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate LICENSE resources.
 *
 * {@maven.plugin.fields}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-license-resources", requiresDependencyResolution = TEST,
      defaultPhase = GENERATE_RESOURCES, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateLicenseResourcesMojo extends AbstractLicenseMojo {
    private static final String COMPILE = "compile";
    private static final String PROVIDED = "provided";
    private static final String RUNTIME = "runtime";
    private static final String SYSTEM = "system";
    private static final String TEST = "test";
    private static final String INCLUDE_SCOPE = COMPILE + "," + RUNTIME;
    private static final String EXCLUDE_SCOPE = "";

    private static final TreeSet<String> ARCHIVE_PACKAGING =
        Stream.of("jar", "maven-plugin", "ejb", "war", "ear", "rar")
        .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

    private static final Comparator<List<AnyLicenseInfo>> LICENSES_ORDER =
        Comparator.<List<AnyLicenseInfo>>
        comparingInt(t -> t.size()).reversed()
        .thenComparing(List::toString);
    private static final Comparator<Row> REPORT_ORDER =
        Comparator
        .comparing(Row::getLicenses, LICENSES_ORDER)
        .thenComparing(t -> t.getArtifacts().get(0), ArtifactModelMap.ORDER);

    @Parameter(defaultValue = "${project.build.outputDirectory}",
               property = "license.resources.directory")
    private File directory = null;

    @Parameter(defaultValue = INCLUDE_SCOPE, property = "license.includeScope")
    private String includeScope = INCLUDE_SCOPE;

    @Parameter(defaultValue = EXCLUDE_SCOPE, property = "license.excludeScope")
    private String excludeScope = EXCLUDE_SCOPE;

    @Inject private MavenProject project = null;
    @Inject private ArtifactAnyLicenseInfoMap artifactAnyLicenseInfoMap = null;
    @Inject private ArtifactModelMap artifactModelMap = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String packaging = project.getPackaging();

            if (ARCHIVE_PACKAGING.contains(packaging)) {
                Set<String> scope = getScope();
                Map<Artifact,List<Artifact>> map =
                    project.getArtifacts()
                    .stream()
                    .filter(t -> scope.contains(t.getScope()))
                    .collect(groupingBy(t -> t,
                                        () -> new TreeMap<>(ArtifactModelMap.ORDER),
                                        toList()));
                List<Row> list =
                    map.values()
                    .stream()
                    .filter(t -> scope.contains(t.get(0).getScope()))
                    .map(t -> new Row(t, artifactAnyLicenseInfoMap.get(t.get(0))))
                    .collect(toList());

                list.sort(REPORT_ORDER);
                /*
                 * LICENSE
                 */
                File file = getFile();

                if (file.exists() && file.isDirectory()) {
                    throw new FileAlreadyExistsException(file.toString(), null,
                                                         "Is not a file or link");
                }

                if (directory.exists() && (! directory.isDirectory())) {
                    throw new FileAlreadyExistsException(directory.toString(), null,
                                                         "Is not a directory");
                }

                Path source = file.toPath();
                Path target = directory.toPath().resolve(source.getFileName());

                Files.createDirectories(directory.toPath());
                Files.copy(file.toPath(), target, REPLACE_EXISTING);
                Files.setLastModifiedTime(target,
                                          Files.getLastModifiedTime(source));
                /*
                 * DEPENDENCIES
                 */
                target = directory.toPath().resolve("DEPENDENCIES");

                try (PrintWriter out =
                         new PrintWriter(Files
                                         .newBufferedWriter(target,
                                                            CREATE, WRITE,
                                                            TRUNCATE_EXISTING))) {
                    out.println(target.getFileName());

                    String header = null;

                    for (Row row : list) {
                        if (! row.getLicenses().toString().equals(header)) {
                            if (header != null) {
                                out.println();
                            }

                            header = row.getLicenses().toString();
                            out.println();
                            out.println(header);
                        }

                        List<Artifact> artifacts = row.getArtifacts();
                        Model model = artifactModelMap.get(artifacts.get(0));

                        out.println();

                        if (StringUtils.isNotBlank(model.getName())) {
                            out.println(model.getName());
                        }

                        for (Artifact artifact : artifacts) {
                            out.println(artifact);
                        }

                        if (StringUtils.isNotBlank(model.getUrl())) {
                            out.println(model.getUrl());
                        }
                    }
                }
            } else {
                log.warn("Skipping for '" + packaging +"' packaging");
            }
        } catch (IOException exception) {
            fail(exception.getMessage(), exception);
        } catch (Throwable throwable) {
            log.error(throwable.getMessage(), throwable);

            if (throwable instanceof MojoExecutionException) {
                throw (MojoExecutionException) throwable;
            } else if (throwable instanceof MojoFailureException) {
                throw (MojoFailureException) throwable;
            } else {
                throw new MojoExecutionException(throwable.getMessage(),
                                                 throwable);
            }
        }
    }

    private Set<String> getScope() {
        TreeSet<String> scope =
            Stream.of(includeScope.split("[\\p{Punct}\\p{Space}]+"))
            .filter(StringUtils::isNotBlank)
            .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

        Stream.of(excludeScope.split("[\\p{Punct}\\p{Space}]+"))
            .filter(StringUtils::isNotBlank)
            .forEach(t -> scope.remove(t));

        if (scope.isEmpty()) {
            log.warn("Specified scope is empty");
            log.debug("        includeScope = " + includeScope);
            log.debug("        excludeScope = " + excludeScope);
        }

        return scope;
    }

    @AllArgsConstructor @Getter @Setter @ToString
    private class Row {
        private List<Artifact> artifacts = null;
        private List<AnyLicenseInfo> licenses = null;
    }
}
