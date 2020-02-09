package ball.maven.plugins.license;

import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

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
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
        } catch (Exception exception) {
            if (exception instanceof MojoExecutionException) {
                throw (MojoExecutionException) exception;
            } else if (exception instanceof MojoFailureException) {
                throw (MojoFailureException) exception;
            } else {
                String message = exception.getMessage();

                throw new MojoFailureException(exception, message, message);
            }
        }
    }
}
