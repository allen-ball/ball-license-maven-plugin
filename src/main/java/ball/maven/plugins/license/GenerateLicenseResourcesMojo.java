package ball.maven.plugins.license;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.util.stream.Collectors.toCollection;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;

/**
 * {@link org.apache.maven.plugin.Mojo} to generate LICENSE resources.
 *
 * {@maven.plugin.fields}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Mojo(name = "generate-license-resources", defaultPhase = GENERATE_RESOURCES, requiresProject = true)
@NoArgsConstructor @ToString @Slf4j
public class GenerateLicenseResourcesMojo extends AbstractLicenseMojo {
    private static final TreeSet<String> ARCHIVE_PACKAGING =
        Stream.of("jar", "maven-plugin", "ejb", "war", "ear", "rar")
        .collect(toCollection(() -> new TreeSet<>()));

    @Parameter(required = true, readonly = true,
               property = "license.resources.directory",
               defaultValue = "${project.build.outputDirectory}")
    @Getter
    private File directory = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            MavenProject project = getProject();
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
            } else {
                getLog().warn("Skipping for '" + packaging +"' packaging");
            }
        } catch (IOException exception) {
            getLog().error(exception.getMessage(), exception);

            throw new MojoFailureException(exception.getMessage(), exception);
        } catch (Throwable throwable) {
            getLog().error(throwable.getMessage(), throwable);

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
