package ball.maven.plugins.license;

import java.io.File;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;

import static org.apache.maven.model.building.ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL;

/**
 * {@link Artifact} to {@link Model} {@link java.util.Map}
 * implementation.  The {@link #get(Object)} method transparently calculates
 * and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class ArtifactModelMap extends TreeMap<Artifact,Model> {
    public static final Comparator<Artifact> ORDER =
        Comparator
        .comparing(Artifact::getGroupId)
        .thenComparing(Artifact::getArtifactId)
        .thenComparing(Artifact::getVersion);

    private final MavenSession session;
    private final ProjectBuilder builder;

    /**
     * Sole constructor.
     *
     * @param   session         The injected {@link MavenSession}.
     * @param   builder         The injected {@link ProjectBuilder}.
     */
    @Inject
    public ArtifactModelMap(MavenSession session, ProjectBuilder builder) {
        super(ORDER);

        this.session = Objects.requireNonNull(session);
        this.builder = Objects.requireNonNull(builder);
    }

    @Override
    public Model get(Object key) {
        Model value = super.get(key);

        if (value == null) {
            MavenProject project = compute((Artifact) key);

            if (project != null) {
                value = project.getModel();

                MavenProject parent = project.getParent();

                while (parent != null) {
                    if (! containsKey(parent.getArtifact())) {
                        put(parent.getArtifact(), parent.getModel());
                    }

                    parent = parent.getParent();
                }

                put(project.getArtifact(), value);
            } else {
                log.warn(key + ": No Model computed");
            }
        }

        return value;
    }

    private MavenProject compute(Artifact artifact) {
        MavenProject project = null;
        String name =
            artifact.getArtifactId() + "-" + artifact.getVersion() + ".pom";
        File file = new File(artifact.getFile().getParentFile(), name);
        ProjectBuildingRequest request =
            new DefaultProjectBuildingRequest(session.getProjectBuildingRequest())
            .setValidationLevel(VALIDATION_LEVEL_MINIMAL)
            .setResolveDependencies(false)
            .setProcessPlugins(false);

        try {
            project = builder.build(file, request).getProject();

            project.getArtifact().setScope(artifact.getScope());
            project.getArtifact().setGroupId(artifact.getGroupId());
            project.getArtifact().setArtifactId(artifact.getArtifactId());
            project.getArtifact().setVersion(artifact.getVersion());

            project.setGroupId(artifact.getGroupId());
            project.setArtifactId(artifact.getArtifactId());
            project.setVersion(artifact.getVersion());
        } catch (Exception exception) {
            log.warn("Cannot read POM for " + artifact);
        }

        return project;
    }
}
