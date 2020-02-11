package ball.maven.plugins.license;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import static lombok.AccessLevel.PROTECTED;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Abstract base class for license {@link org.apache.maven.plugin.Mojo}s.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor @ToString @Slf4j
public abstract class AbstractLicenseMojo extends AbstractMojo
                                          implements Contextualizable {
    protected PlexusContainer container = null;

    @Parameter(required = true, readonly = true, defaultValue = "${session}")
    @Getter
    private MavenSession session = null;

    @Parameter(required = true, readonly = true, defaultValue = "${settings}")
    @Getter
    private Settings settings = null;

    @Parameter(required = true, readonly = true,
               defaultValue = "${mojoExecution}")
    @Getter
    private MojoExecution mojo = null;

    @Parameter(required = true, readonly = true, defaultValue = "${plugin}")
    @Getter
    private PluginDescriptor plugin = null;

    @Parameter(required = true, readonly = true, defaultValue = "${project}")
    @Getter
    private MavenProject project = null;

    @Parameter(required = true, readonly = true,
               defaultValue = "${project.licenses}")
    @Getter
    private List<License> licenses = Collections.emptyList();

    @Parameter(required = true, readonly = true,
               property = "license.file", defaultValue = "${basedir}/LICENSE")
    @Getter
    private File file = null;

    @Override
    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }

    protected void copy(URL from, Path to) throws MojoExecutionException,
                                                  MojoFailureException {
        String message = null;

        try {
            message = to.toString() + " <- " + from.toString();

            if (Files.isDirectory(to)) {
                throw new FileAlreadyExistsException(to.toString(), null,
                                                     "Is not a file or link");
            }

            FileTime local = FileTime.fromMillis(0);

            if (Files.exists(to)) {
                local = Files.getLastModifiedTime(to);
            }

            URLConnection connection = from.openConnection();
            FileTime remote =
                FileTime.fromMillis(connection.getLastModified());
            boolean isNewer =
                (! Files.exists(to))
                || remote.toMillis() == 0
                || remote.compareTo(local) > 0;

            if (isNewer) {
                CopyOption[] options =
                    Stream.of(StandardCopyOption.REPLACE_EXISTING)
                    .filter(t -> false)
                    .toArray(CopyOption[]::new);

                try (InputStream in = connection.getInputStream()) {
                    Files.copy(in, to, options);
                }

                if (remote.toMillis() != 0) {
                    Files.setLastModifiedTime(to, remote);
                }

                getLog().info(message);
            } else {
                getLog().info(to + " is up-to-date");
            }
        } catch (IOException exception) {
            getLog().error(message, exception);

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
