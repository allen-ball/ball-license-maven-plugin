package ball.maven.plugins.license;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.util.stream.Collectors.toCollection;
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
    private static final TreeSet<String> ARCHIVE_PACKAGING =
        Stream.of("jar", "maven-plugin", "ejb", "war", "ear", "rar")
        .collect(toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    @Inject
    private MavenProject project = null;

    @Inject
    private ArtifactProjectMap map = null;

    @Parameter(defaultValue = "${project.build.outputDirectory}",
               property = "license.resources.directory")
    @Getter
    private File directory = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String packaging = project.getPackaging();

            if (ARCHIVE_PACKAGING.contains(packaging)) {
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
                Files.copy(file.toPath(), target,
                           StandardCopyOption.REPLACE_EXISTING);
                Files.setLastModifiedTime(target,
                                          Files.getLastModifiedTime(source));

                for (Artifact artifact : project.getArtifacts()) {
log.info(String.valueOf(artifact.getId()));
log.info(String.valueOf(map.get(artifact).getLicenses()));
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
}
