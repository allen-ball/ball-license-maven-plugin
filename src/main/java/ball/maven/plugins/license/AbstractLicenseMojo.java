package ball.maven.plugins.license;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import static lombok.AccessLevel.PROTECTED;

/**
 * Abstract base class for license {@link org.apache.maven.plugin.Mojo}s.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor(access = PROTECTED) @ToString @Slf4j
public abstract class AbstractLicenseMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.licenses}", readonly = true)
    @Getter
    private List<License> licenses = Collections.emptyList();

    @Parameter(defaultValue = "${basedir}/LICENSE",
               property = "license.file", readonly = true)
    @Getter
    private File file = null;

    @PostConstruct
    public void init() {
    }

    @PreDestroy
    public void destroy() {
    }

    protected void copy(String from, Path to) throws MojoExecutionException,
                                                     MojoFailureException {
        try {
            copy(new URL(from), to);
        } catch (MalformedURLException exception) {
            fail(from, exception);
        }
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

                log.info(message);
            } else {
                log.info(to + " is up-to-date");
            }
        } catch (IOException exception) {
            fail(message, exception);
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

    /**
     * Log and throw a {@link MojoFailureException}.
     *
     * @param   message         The message {@link String}.
     *
     * @throws  MojoFailureException
     *                          Always.
     */
    protected void fail(String message) throws MojoFailureException {
        log.error(message);
        throw new MojoFailureException(message);
    }

    /**
     * Log and throw a {@link MojoFailureException}.
     *
     * @param   message         The message {@link String}.
     * @param   reason          The reason {@link Throwable}.
     *
     * @throws  MojoFailureException
     *                          Always.
     */
    protected void fail(String message,
                        Throwable reason) throws MojoFailureException {
        log.error(message, reason);
        throw new MojoFailureException(message, reason);
    }
}
