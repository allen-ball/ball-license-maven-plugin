package ball.maven.plugins.license;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
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

    @Parameter(required = true, readonly = true, defaultValue = "${mojoExecution}")
    @Getter
    private MojoExecution mojo = null;

    @Parameter(required = true, readonly = true, defaultValue = "${plugin}")
    @Getter
    private PluginDescriptor plugin = null;

    @Parameter(required = true, readonly = true, defaultValue = "${project}")
    @Getter
    private MavenProject project = null;

    @Parameter(required = true, readonly = true,
               property = "license.file", defaultValue = "${basedir}/LICENSE")
    @Getter
    private File file = null;

    @Override
    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }
}
