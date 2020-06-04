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
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.project.DefaultProjectBuildingRequest;
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
public class ArtifactModelCache extends TreeMap<Artifact,Model> {
    private static final long serialVersionUID = 5197054494150680897L;

    public static final Comparator<Artifact> ORDER =
        Comparator
        .comparing(Artifact::getGroupId)
        .thenComparing(Artifact::getArtifactId)
        .thenComparing(Artifact::getVersion);

    /** @serial */ private final MavenSession session;
    /** @serial */ private final ProjectBuilder builder;
    /** @serial */ private final ModelReader reader;

    /**
     * Sole constructor.
     *
     * @param   session         The injected {@link MavenSession}.
     * @param   builder         The injected {@link ProjectBuilder}.
     * @param   reader          The injected {@link ModelReader}.
     */
    @Inject
    public ArtifactModelCache(MavenSession session,
                              ProjectBuilder builder, ModelReader reader) {
        super(ORDER);

        this.session = Objects.requireNonNull(session);
        this.builder = Objects.requireNonNull(builder);
        this.reader = Objects.requireNonNull(reader);
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    @Override
    public Model get(Object key) {
        Model value = super.get(key);

        if (value == null) {
            value = compute((Artifact) key);

            put((Artifact) key, value);
        }

        return value;
    }

    private Model compute(Artifact artifact) {
        File file =
            new File(artifact.getFile().getParentFile(),
                     artifact.getArtifactId()
                     + "-" + artifact.getVersion() + ".pom");
        Model model =
            session.getProjects()
            .stream()
            .filter(t -> ORDER.compare(t.getArtifact(), artifact) == 0)
            .map(t -> t.getModel())
            .filter(Objects::nonNull)
            .findFirst().orElse(null);

        if (model == null) {
            try {
                model = reader.read(file, null);
            } catch (Exception exception) {
                log.debug("Cannot read POM for " + artifact);
                /* log.debug(exception.getMessage(), exception); */
            }

            if (model != null
                && (model.getLicenses() == null
                    || model.getLicenses().isEmpty())) {
                try {
                    ProjectBuildingRequest request =
                        new DefaultProjectBuildingRequest(session.getProjectBuildingRequest())
                        .setValidationLevel(VALIDATION_LEVEL_MINIMAL)
                        .setResolveDependencies(false)
                        .setProcessPlugins(false);

                    model =
                        builder.build(file, request)
                        .getProject().getModel();
                } catch (Exception exception) {
                    log.debug("Cannot load POM for " + artifact);
                    /* log.debug(exception.getMessage(), exception); */
                }
            }
        }

        return model;
    }
}
