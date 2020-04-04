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
import java.util.Set;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;

import static lombok.AccessLevel.PROTECTED;
import static java.util.stream.Collectors.toSet;

/**
 * Abstract base class for license {@link org.apache.maven.plugin.Mojo}s.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor(access = PROTECTED) @ToString @Slf4j
public abstract class AbstractLicenseMojo extends AbstractMojo {
    @Parameter(defaultValue = "${basedir}/LICENSE",
               property = "license.file", readonly = true)
    @Getter
    private File file = null;

    protected void warnIfExtractedLicenseInfo(Stream<AnyLicenseInfo> stream) {
        Set<ExtractedLicenseInfo> extracted =
            stream
            .flatMap(t -> LicenseUtilityMethods.walk(t))
            .filter(t -> (t instanceof ExtractedLicenseInfo))
            .map(t -> (ExtractedLicenseInfo) t)
            .collect(toSet());

        if (! extracted.isEmpty()) {
            log.warn("Cannot match to SPDX license(s)");

            for (ExtractedLicenseInfo license : extracted) {
                String id = license.getLicenseId();

                log.warn("    '" + license.getLicenseId() + "'");

                String[] seeAlso = license.getSeeAlso();

                if (seeAlso != null) {
                    for (String string : seeAlso) {
                        if (! string.equals(license.getLicenseId())) {
                            log.warn("        " + string);
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to copy from {@link URL} ({@link String} representation) to
     * local {@link Path}.
     *
     * @param   from            The source {@link URL} ({@link String}
     *                          representation).
     * @param   to              The target {@link Path}.
     * @param   overwrite       Whether or not to overwrite an existing
     *                          {@link Path}.
     *
     * @throws  MojoExecutionException
     *                          See
     *                          {@link org.apache.maven.plugin.Mojo#execute()}.
     * @throws  MojoFailureException
     *                          See
     *                          {@link org.apache.maven.plugin.Mojo#execute()}.
     */
    protected void copy(String from, Path to,
                        boolean overwrite) throws MojoExecutionException,
                                                  MojoFailureException {
        try {
            copy(new URL(from), to, overwrite);
        } catch (MalformedURLException exception) {
            fail(from, exception);
        }
    }

    /**
     * Method to copy from {@link URL} ({@link String} representation) to
     * local {@link Path}.
     *
     * @param   from            The source {@link URL}.
     * @param   to              The target {@link Path}.
     * @param   overwrite       Whether or not to overwrite an existing
     *                          {@link Path}.
     *
     * @throws  MojoExecutionException
     *                          See
     *                          {@link org.apache.maven.plugin.Mojo#execute()}.
     * @throws  MojoFailureException
     *                          See
     *                          {@link org.apache.maven.plugin.Mojo#execute()}.
     */
    protected void copy(URL from, Path to,
                        boolean overwrite) throws MojoExecutionException,
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
                    .filter(t -> isNewer & overwrite)
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
