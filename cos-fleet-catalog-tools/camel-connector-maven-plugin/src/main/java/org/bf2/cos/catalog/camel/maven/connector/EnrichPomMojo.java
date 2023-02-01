package org.bf2.cos.catalog.camel.maven.connector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorDependency;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;

import io.fabric8.maven.Maven;

@Mojo(name = "enrich-pom", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class EnrichPomMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "cos.pom.enrich.skip")
    private boolean skip = false;
    @Parameter(defaultValue = "true", property = "cos.pom.enrich.fail")
    private boolean fail = true;
    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;
    @Parameter
    private Connector defaults;
    @Parameter
    private List<Connector> connectors;
    @Parameter
    private List<String> bannedDependencies;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping POM Enricher");
            return;
        }

        String oldDigest = pomChecksum();
        enrichPom();
        String newDigest = pomChecksum();

        if (fail && !Objects.equals(oldDigest, newDigest)) {
            throw new MojoExecutionException(
                    "The dependencies have changed and the pom.xml has been overwritten, please rebuild");
        }
    }

    private void enrichPom() throws MojoExecutionException {
        Model model = Maven.readModel(project.getFile().toPath());
        model.getDependencies().clear();

        Stream.concat(localDependencies().stream(), injectedDependencies().stream())
                .distinct()
                .map(this::asMavenDependency)
                .forEach(d -> model.getDependencies().add(d));

        getLog().info("Writing pom.xml");

        Maven.writeModel(model);
    }

    private String pomChecksum() throws MojoExecutionException {
        try (InputStream is = Files.newInputStream(project.getFile().toPath())) {
            return DigestUtils.sha256Hex(is);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private Dependency asMavenDependency(ConnectorDependency cd) {
        Dependency d = new Dependency();
        d.setGroupId(cd.groupId);
        d.setArtifactId(cd.artifactId);

        return d;
    }

    private Set<ConnectorDependency> localDependencies() {
        Set<ConnectorDependency> result = new TreeSet<>(Comparator.comparing(ConnectorDependency::toString));

        project.getDependencies().stream()
                .filter(d -> !"provided".equals(d.getScope()))
                .filter(d -> Objects.equals(project.getFile().toString(), d.getLocation("").getSource().getLocation()))
                .map(cd -> new ConnectorDependency(cd.getGroupId(), cd.getArtifactId()))
                .filter(cd -> !isBanned(cd))
                .forEach(result::add);

        return result;
    }

    private Set<ConnectorDependency> injectedDependencies() throws MojoExecutionException {
        Set<ConnectorDependency> result = new TreeSet<>(Comparator.comparing(ConnectorDependency::toString));

        var projectDeps = project.getDependencies().stream()
                .filter(d -> !"provided".equals(d.getScope()))
                .map(cd -> new ConnectorDependency(cd.getGroupId(), cd.getArtifactId()))
                .filter(cd -> !isBanned(cd))
                .collect(Collectors.toSet());

        try {
            for (Connector connector : MojoSupport.inject(session, defaults, connectors)) {
                Collection<ConnectorDependency> deps = ConnectorSupport.dependencies(
                        KameletsCatalog.get(project, getLog()),
                        connector,
                        camelQuarkusVersion);

                deps.stream()
                        .filter(cd -> !isBanned(cd))
                        .map(cd -> new ConnectorDependency(cd.groupId, cd.artifactId))
                        .filter(cd -> !projectDeps.contains(cd))
                        .forEach(result::add);
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }

        return result;
    }

    private boolean isBanned(ConnectorDependency cd) {
        return bannedDependencies != null && bannedDependencies.contains(cd.groupId + ":" + cd.artifactId);
    }
}
