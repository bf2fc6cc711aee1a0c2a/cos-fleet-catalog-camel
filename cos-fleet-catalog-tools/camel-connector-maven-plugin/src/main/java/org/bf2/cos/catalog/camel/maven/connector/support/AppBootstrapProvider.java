package org.bf2.cos.catalog.camel.maven.connector.support;

import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.model.AppDependency;
import io.quarkus.bootstrap.model.PathsCollection;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class AppBootstrapProvider {
    private AppArtifact appArtifact;
    private MavenArtifactResolver artifactResolver;
    private QuarkusBootstrap quarkusBootstrap;

    private RepositorySystem repoSystem;
    private RepositorySystemSession repoSession;
    private List<RemoteRepository> repos;
    private RemoteRepositoryManager remoteRepoManager;
    private String camelQuarkusVersion;
    private MavenSession session;
    private Connector defaults;
    private List<Connector> connectors;
    private MavenProject project;
    private Log log;
    private String finalName;
    private File buildDir;
    private Map<String, String> properties;
    private String[] ignoredEntries;
    private String appArtifactCoords;

    public RepositorySystem getRepoSystem() {
        return repoSystem;
    }

    public void setRepoSystem(RepositorySystem repoSystem) {
        this.repoSystem = repoSystem;
    }

    public RepositorySystemSession getRepoSession() {
        return repoSession;
    }

    public void setRepoSession(RepositorySystemSession repoSession) {
        this.repoSession = repoSession;
    }

    public List<RemoteRepository> getRepos() {
        return repos;
    }

    public void setRepos(List<RemoteRepository> repos) {
        this.repos = repos;
    }

    public RemoteRepositoryManager getRemoteRepoManager() {
        return remoteRepoManager;
    }

    public void setRemoteRepoManager(RemoteRepositoryManager remoteRepoManager) {
        this.remoteRepoManager = remoteRepoManager;
    }

    public String getCamelQuarkusVersion() {
        return camelQuarkusVersion;
    }

    public void setCamelQuarkusVersion(String camelQuarkusVersion) {
        this.camelQuarkusVersion = camelQuarkusVersion;
    }

    public MavenSession getSession() {
        return session;
    }

    public void setSession(MavenSession session) {
        this.session = session;
    }

    public Connector getDefaults() {
        return defaults;
    }

    public void setDefaults(Connector defaults) {
        this.defaults = defaults;
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<Connector> connectors) {
        this.connectors = connectors;
    }

    public MavenProject getProject() {
        return project;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public Log getLog() {
        return log;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public String getFinalName() {
        return finalName;
    }

    public void setFinalName(String finalName) {
        this.finalName = finalName;
    }

    public File getBuildDir() {
        return buildDir;
    }

    public void setBuildDir(File buildDir) {
        this.buildDir = buildDir;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public String[] getIgnoredEntries() {
        return ignoredEntries;
    }

    public void setIgnoredEntries(String[] ignoredEntries) {
        this.ignoredEntries = ignoredEntries;
    }

    public String getAppArtifactCoords() {
        return appArtifactCoords;
    }

    public void setAppArtifactCoords(String appArtifactCoords) {
        this.appArtifactCoords = appArtifactCoords;
    }

    private MavenArtifactResolver artifactResolver()
            throws MojoExecutionException {
        if (artifactResolver != null) {
            return artifactResolver;
        }
        try {
            return artifactResolver = MavenArtifactResolver.builder()
                    .setWorkspaceDiscovery(false)
                    .setRepositorySystem(repoSystem)
                    .setRepositorySystemSession(repoSession)
                    .setRemoteRepositories(repos)
                    .setRemoteRepositoryManager(remoteRepoManager)
                    .build();
        } catch (BootstrapMavenException e) {
            throw new MojoExecutionException("Failed to initialize Quarkus bootstrap Maven artifact resolver", e);
        }
    }

    public QuarkusBootstrap bootstrapQuarkus() throws MojoExecutionException {
        if (quarkusBootstrap != null) {
            return quarkusBootstrap;
        }

        if (camelQuarkusVersion == null) {
            throw new MojoExecutionException("The camelQuarkusVersion should be configured on the plugin");
        }

        final Set<ConnectorDependency> connectorsDependecies = new HashSet<>();
        final Set<ConnectorDependency> projectDependencies = new HashSet<>();

        for (Connector connector : MojoSupport.inject(session, defaults, connectors)) {
            try {
                connectorsDependecies.addAll(
                        ConnectorSupport.dependencies(
                                KameletsCatalog.get(project, getLog()),
                                connector,
                                camelQuarkusVersion));
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }

        for (org.apache.maven.model.Dependency dependency : project.getDependencies()) {
            if (!Objects.equals(dependency.getScope(), "provided")) {
                projectDependencies.add(
                        new ConnectorDependency(
                                dependency.getGroupId(),
                                dependency.getArtifactId(),
                                dependency.getVersion()));
            }
        }

        getLog().info("Connectors dependencies:");
        connectorsDependecies.stream()
                .distinct()
                .sorted(Comparator.comparing(ConnectorDependency::toString))
                .forEach(d -> getLog().info("- " + d));

        getLog().info("Project dependencies:");
        projectDependencies.stream()
                .distinct()
                .sorted(Comparator.comparing(ConnectorDependency::toString))
                .forEach(d -> getLog().info("- " + d));

        final Properties projectProperties = project.getProperties();
        final Properties effectiveProperties = new Properties();
        // quarkus. properties > ignoredEntries in pom.xml
        if (ignoredEntries != null && ignoredEntries.length > 0) {
            String joinedEntries = String.join(",", ignoredEntries);
            effectiveProperties.setProperty("quarkus.package.user-configured-ignored-entries", joinedEntries);
        }
        for (String name : projectProperties.stringPropertyNames()) {
            if (name.startsWith("quarkus.")) {
                effectiveProperties.setProperty(name, projectProperties.getProperty(name));
            }
        }

        // Add plugin properties
        if (properties != null) {
            effectiveProperties.putAll(properties);
        }

        effectiveProperties.putIfAbsent("quarkus.application.name", project.getArtifactId());
        effectiveProperties.putIfAbsent("quarkus.application.version", project.getVersion());

        QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                .setAppArtifact(appArtifact())
                .setManagingProject(managingProject())
                .setMavenArtifactResolver(artifactResolver())
                .setIsolateDeployment(true)
                .setBaseClassLoader(getClass().getClassLoader())
                .setBuildSystemProperties(effectiveProperties)
                .setLocalProjectDiscovery(false)
                .setProjectRoot(project.getBasedir().toPath())
                .setBaseName(finalName)
                .setTargetDirectory(buildDir.toPath())
                .setForcedDependencies(
                        Stream.concat(connectorsDependecies.stream(), projectDependencies.stream())
                                .distinct()
                                .map(d -> new AppArtifact(d.groupId, d.artifactId, d.version))
                                .map(d -> new AppDependency(d, "compile"))
                                .collect(Collectors.toList()));

        for (MavenProject project : project.getCollectedProjects()) {
            builder.addLocalArtifact(new AppArtifactKey(project.getGroupId(), project.getArtifactId(), null,
                    project.getArtifact().getArtifactHandler().getExtension()));
        }

        return quarkusBootstrap = builder.build();
    }

    protected AppArtifact managingProject() {
        if (appArtifact == null) {
            return null;
        }
        final Artifact artifact = project.getArtifact();
        return new AppArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier(), artifact.getArtifactHandler().getExtension(),
                artifact.getVersion());
    }

    private AppArtifact appArtifact() throws MojoExecutionException {
        return appArtifact == null ? appArtifact = initAppArtifact() : appArtifact;
    }

    private AppArtifact initAppArtifact() throws MojoExecutionException {
        String appArtifactCoords = AppBootstrapProvider.this.appArtifactCoords;
        if (appArtifactCoords == null) {
            final Artifact projectArtifact = project.getArtifact();
            final AppArtifact appArtifact = new AppArtifact(projectArtifact.getGroupId(), projectArtifact.getArtifactId(),
                    projectArtifact.getClassifier(), projectArtifact.getArtifactHandler().getExtension(),
                    projectArtifact.getVersion());

            File projectFile = projectArtifact.getFile();
            if (projectFile == null) {
                projectFile = new File(project.getBuild().getOutputDirectory());
                if (!projectFile.exists()) {
                    /*
                     * TODO GenerateCodeMojo would fail
                     * if (hasSources(project)) {
                     * throw new MojoExecutionException("Project " + project.getArtifact() + " has not been compiled yet");
                     * }
                     */
                    if (!projectFile.mkdirs()) {
                        throw new MojoExecutionException("Failed to create the output dir " + projectFile);
                    }
                }
            }
            appArtifact.setPaths(PathsCollection.of(projectFile.toPath()));
            return appArtifact;
        }

        final String[] coordsArr = appArtifactCoords.split(":");
        if (coordsArr.length < 2 || coordsArr.length > 5) {
            throw new MojoExecutionException(
                    "appArtifact expression " + appArtifactCoords
                            + " does not follow format groupId:artifactId:classifier:type:version");
        }
        final String groupId = coordsArr[0];
        final String artifactId = coordsArr[1];
        String classifier = "";
        String type = "jar";
        String version = null;
        if (coordsArr.length == 3) {
            version = coordsArr[2];
        } else if (coordsArr.length > 3) {
            classifier = coordsArr[2] == null ? "" : coordsArr[2];
            type = coordsArr[3] == null ? "jar" : coordsArr[3];
            if (coordsArr.length > 4) {
                version = coordsArr[4];
            }
        }
        if (version == null) {
            for (Artifact dep : project.getArtifacts()) {
                if (dep.getArtifactId().equals(artifactId)
                        && dep.getGroupId().equals(groupId)
                        && dep.getClassifier().equals(classifier)
                        && dep.getType().equals(type)) {
                    return new AppArtifact(dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getClassifier(),
                            dep.getArtifactHandler().getExtension(),
                            dep.getVersion());
                }
            }
            throw new IllegalStateException("Failed to locate " + appArtifactCoords + " among the project dependencies");
        }

        final AppArtifact appArtifact = new AppArtifact(groupId, artifactId, classifier, type, version);
        try {
            appArtifact.setPath(
                    artifactResolver().resolve(new DefaultArtifact(groupId, artifactId, classifier, type, version))
                            .getArtifact().getFile().toPath());
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve " + appArtifact, e);
        }
        return appArtifact;
    }
}