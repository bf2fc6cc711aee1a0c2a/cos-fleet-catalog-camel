package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.bf2.cos.catalog.camel.maven.connector.model.ConnectorDefinition;
import org.bf2.cos.catalog.camel.maven.connector.support.Annotation;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogConstants;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorIndex;
import org.bf2.cos.catalog.camel.maven.connector.support.ConnectorManifest;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;
import org.bf2.cos.catalog.camel.maven.connector.validator.Validator;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import net.javacrumbs.jsonunit.core.Option;

import static java.util.Optional.ofNullable;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.JSON_MAPPER;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.addRequired;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.asKey;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.computeDataShapes;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.computeErrorHandler;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.copyProperties;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.dataShape;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.kameletType;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "cos.catalog.skip")
    private boolean skip = false;

    @Parameter(defaultValue = "false", property = "cos.connector.groups")
    private boolean groups = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter
    private List<Annotation> defaultAnnotations;
    @Parameter
    private List<Annotation> annotations;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;
    @Parameter
    private Connector defaults;
    @Parameter
    private List<Connector> connectors;

    @Parameter(defaultValue = "true", property = "cos.catalog.validate")
    private boolean validate;

    @Parameter(defaultValue = "${project.artifactId}", property = "cos.connector.type")
    private String type;
    @Parameter(defaultValue = "${project.version}", property = "cos.connector.version")
    private String version;
    @Parameter(defaultValue = "0", property = "cos.connector.initial-revision")
    private int initialRevision;
    @Parameter(defaultValue = "cos-connector", property = "cos.connector.container.image-prefix")
    private String containerImagePrefix;
    @Parameter(property = "cos.connector.container.registry")
    private String containerImageRegistry;
    @Parameter(defaultValue = "${project.groupId}", property = "cos.connector.container.organization")
    private String containerImageOrg;
    @Parameter(property = "cos.base.container.image")
    private String containerImageBase;
    @Parameter(property = "cos.connector.container.tag", required = true)
    private String containerImageTag;

    @Parameter
    private List<File> validators;
    @Parameter(defaultValue = "FAIL", property = "cos.catalog.validation.mode")
    private Validator.Mode mode;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/connectors")
    private File definitionPathLocal;
    @Parameter(defaultValue = "${cos.connector.catalog.root}/${cos.catalog.name}")
    private File definitionPath;
    @Parameter(defaultValue = "${cos.connector.catalog.root}")
    private File indexPath;
    @Parameter(defaultValue = "${cos.catalog.name}")
    private String catalogName;

    @Parameter(required = false, property = "appArtifact")
    private String appArtifact;
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;
    @Parameter(defaultValue = "${project.build.finalName}")
    protected String finalName;
    @Parameter(defaultValue = "${camel-quarkus.version}")
    private String camelQuarkusVersion;
    @Requirement(role = RepositorySystem.class, optional = false)
    protected RepositorySystem repoSystem;
    @Requirement(role = RemoteRepositoryManager.class, optional = false)
    protected RemoteRepositoryManager remoteRepoManager;
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;
    @Parameter
    private Map<String, String> systemProperties;

    @Component
    protected MavenProjectHelper projectHelper;

    ConnectorManifest manifest;
    String manifestId;
    Path manifestFile;
    Path manifestLocalFile;
    Path indexFile;
    ConnectorIndex index;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping generate-catalog");
        }

        try {
            this.manifestId = type.replace("-", "_");
            this.indexFile = indexPath.toPath().resolve("connectors.json");
            this.manifestFile = definitionPath.toPath().resolve(this.manifestId + ".json");
            this.manifestLocalFile = definitionPathLocal.toPath().resolve(this.manifestId + ".json");
            this.index = MojoSupport.load(indexFile, ConnectorIndex.class, ConnectorIndex::new);

            this.manifest = index.getConnectors().computeIfAbsent(this.manifestId, k -> {
                return new ConnectorManifest(
                        this.catalogName,
                        this.initialRevision,
                        Collections.emptySet(),
                        null,
                        this.containerImageBase,
                        null);
            });

            final KameletsCatalog kameletsCatalog = KameletsCatalog.get(project, getLog());
            final List<Connector> connectorList = MojoSupport.inject(session, defaults, connectors);

            //
            // Update manifest dependencies
            //

            TreeSet<String> newDependencies = new TreeSet<>(dependencies());

            if (!this.manifest.getDependencies().equals(newDependencies)) {
                SetUtils.SetView<String> diff = SetUtils.difference(this.manifest.getDependencies(), newDependencies);
                if (diff.isEmpty()) {
                    diff = SetUtils.difference(newDependencies, this.manifest.getDependencies());
                }

                if (!diff.isEmpty()) {
                    getLog().info("Detected diff in dependencies (" + diff.size() + "):");
                    diff.forEach(d -> {
                        getLog().info("  " + d);
                    });
                } else {
                    getLog().info("Detected diff in dependencies (" + diff.size() + ")");
                }

                this.manifest.bump();
                this.manifest.getDependencies().clear();
                this.manifest.getDependencies().addAll(newDependencies);
            }

            if (!Objects.equals(manifest.getBaseImage(), this.containerImageBase)) {
                getLog().info("Detected diff in base image");

                this.manifest.setBaseImage(this.containerImageBase);
                this.manifest.bump();
            }

            //
            // Connectors
            //

            for (Connector connector : connectorList) {
                ConnectorDefinition def = generateDefinitions(kameletsCatalog, connector);

                this.manifest.getTypes().add(def.getConnectorType().getId());
            }

            //
            // Manifest
            //

            getLog().info("Writing connector manifest to: " + manifestLocalFile);

            this.manifest.setImage(
                    String.format("%s/%s/%s-%s:%s",
                            this.containerImageRegistry,
                            this.containerImageOrg,
                            this.containerImagePrefix, this.type,
                            this.containerImageTag));

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(manifestLocalFile),
                    this.manifest);
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private ConnectorDefinition generateDefinitions(KameletsCatalog kamelets, Connector connector)
            throws MojoExecutionException, MojoFailureException {

        final Connector.EndpointRef kafka = connector.getKafka();
        final Connector.EndpointRef adapter = connector.getAdapter();

        if (kafka.getPrefix() == null) {
            throw new MojoExecutionException("Kamelet Kafka prefix is required");
        }
        if (!Character.isLetterOrDigit(kafka.getPrefix().charAt(kafka.getPrefix().length() - 1))) {
            throw new MojoExecutionException("Kamelet Kafka prefix should end with a letter or digit");
        }
        if (adapter.getPrefix() == null) {
            throw new MojoExecutionException("Kamelet Adapter prefix is required");
        }
        if (!Character.isLetterOrDigit(adapter.getPrefix().charAt(connector.getAdapter().getPrefix().length() - 1))) {
            throw new MojoExecutionException("Kamelet Adapter prefix should end with a letter or digit");
        }

        try {
            final ObjectNode adapterSpec = kamelets.kamelet(
                    adapter.getName(),
                    adapter.getVersion());
            final ObjectNode kafkaSpec = kamelets.kamelet(
                    kafka.getName(),
                    kafka.getVersion());

            final String version = ofNullable(connector.getVersion()).orElseGet(project::getVersion);
            final String name = ofNullable(connector.getName()).orElseGet(project::getArtifactId);
            final String title = ofNullable(connector.getTitle()).orElseGet(project::getName);
            final String description = ofNullable(connector.getDescription()).orElseGet(project::getDescription);
            final String type = kameletType(adapterSpec);
            final String id = name.replace("-", "_");

            final Path definitionFile = definitionPath.toPath().resolve(id + ".json");
            final Path definitionLocalFile = definitionPathLocal.toPath().resolve(id + ".json");

            ConnectorDefinition def = new ConnectorDefinition();
            def.getConnectorType().setId(id);
            def.getConnectorType().setKind("ConnectorType");
            def.getConnectorType().setIconRef("TODO");
            def.getConnectorType().setName(title);
            def.getConnectorType().setDescription(description);
            def.getConnectorType().setVersion(version);
            def.getConnectorType().getLabels().add(kameletType(adapterSpec));
            def.getConnectorType().setSchema(JSON_MAPPER.createObjectNode());
            def.getConnectorType().getSchema().put("type", "object");
            def.getConnectorType().getSchema().put("additionalProperties", false);

            //
            // Adapter
            //

            addRequired(
                    groups,
                    adapter,
                    adapterSpec,
                    def.getConnectorType().getSchema());
            copyProperties(
                    groups,
                    adapter,
                    adapterSpec,
                    def.getConnectorType().getSchema());

            //
            // Kafka
            //

            addRequired(
                    groups,
                    kafka,
                    kafkaSpec,
                    def.getConnectorType().getSchema());
            copyProperties(
                    groups,
                    kafka,
                    kafkaSpec,
                    def.getConnectorType().getSchema());

            //
            // DataShape
            //

            var ds = connector.getDataShape();
            if (ds == null) {
                ds = new Connector.DataShapeDefinition();
            }

            computeDataShapes(ds, adapterSpec);

            dataShape(ds.getConsumes(), def, Connector.DataShape.Type.CONSUMES);
            dataShape(ds.getProduces(), def, Connector.DataShape.Type.PRODUCES);

            //
            // ErrorHandler
            //

            if (connector.getErrorHandler() != null && connector.getErrorHandler().getStrategies() != null) {
                computeErrorHandler(def, connector);
            }

            // force capabilities if defined
            if (connector.getCapabilities() != null) {
                def.getConnectorType().getCapabilities().addAll(connector.getCapabilities());
            }

            for (String capability : def.getConnectorType().getCapabilities()) {
                switch (capability) {
                    case CatalogConstants.CAPABILITY_PROCESSORS:
                        def.getConnectorType().getSchema()
                                .with("properties")
                                .with(CatalogConstants.CAPABILITY_PROCESSORS);
                        break;
                    case CatalogConstants.CAPABILITY_ERROR_HANDLER:
                        def.getConnectorType().getSchema()
                                .with("properties")
                                .with(CatalogConstants.CAPABILITY_ERROR_HANDLER);
                        break;
                    case CatalogConstants.CAPABILITY_DATA_SHAPE:
                        def.getConnectorType().getSchema()
                                .with("properties")
                                .with(CatalogConstants.CAPABILITY_DATA_SHAPE);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported capability: " + capability);
                }
            }

            //
            // channels
            //

            if (connector.getChannels() != null) {
                for (var ch : connector.getChannels().entrySet()) {
                    ConnectorDefinition.Channel channel = new ConnectorDefinition.Channel();
                    ConnectorDefinition.Metadata metadata = channel.getMetadata();

                    // add channel to the connector definition
                    def.getConnectorType().getChannels().add(ch.getKey());

                    def.getChannels().put(ch.getKey(), channel);

                    metadata.setConnectorImage("placeholder");
                    metadata.setConnectorRevision(this.initialRevision);
                    metadata.setConnectorType(type);

                    metadata.getOperators().add(new ConnectorDefinition.Operator(
                            ch.getValue().getOperatorType(),
                            ch.getValue().getOperatorVersion()));

                    metadata.getKamelets().getAdapter().setName(adapter.getName());
                    metadata.getKamelets().getAdapter().setPrefix(asKey(adapter.getPrefix()));

                    metadata.getKamelets().getKafka().setName(kafka.getName());
                    metadata.getKamelets().getKafka().setPrefix(kafka.getPrefix());

                    if (Objects.equals(CatalogConstants.SOURCE, kameletType(adapterSpec))) {
                        if (ds.getConsumes() == null && ds.getProduces() != null) {
                            ds.setConsumes(ds.getProduces());
                        }
                    }
                    if (Objects.equals(CatalogConstants.SINK, kameletType(adapterSpec))) {
                        if (ds.getProduces() == null && ds.getConsumes() != null) {
                            ds.setProduces(ds.getConsumes());
                        }
                    }

                    if (ds.getConsumes() != null) {
                        metadata.setConsumes(ds.getConsumes().getDefaultFormat());
                        metadata.setConsumesClass(ds.getConsumes().getContentClass());
                    }
                    if (ds.getProduces() != null) {
                        metadata.setProduces(ds.getProduces().getDefaultFormat());
                        metadata.setProducesClass(ds.getProduces().getContentClass());
                    }

                    if (defaultAnnotations != null) {
                        defaultAnnotations.stream().sorted(Comparator.comparing(Annotation::getName)).forEach(annotation -> {
                            metadata.getAnnotations().put(annotation.getName(), annotation.getValue());
                        });
                    }

                    if (annotations != null) {
                        annotations.stream().sorted(Comparator.comparing(Annotation::getName)).forEach(annotation -> {
                            metadata.getAnnotations().put(annotation.getName(), annotation.getValue());
                        });
                    }

                    if (connector.getErrorHandler() != null && connector.getErrorHandler().getDefaultStrategy() != null) {
                        metadata.setErrorHandlerStrategy(
                                connector.getErrorHandler().getDefaultStrategy().name().toLowerCase(Locale.US));
                    }
                }
            }

            //
            // Disable additional properties if empty capabilities
            //

            CatalogSupport.disableAdditionalProperties(
                    def.getConnectorType().getSchema(),
                    "/properties/" + CatalogConstants.CAPABILITY_PROCESSORS);
            CatalogSupport.disableAdditionalProperties(
                    def.getConnectorType().getSchema(),
                    "/properties/" + CatalogConstants.CAPABILITY_ERROR_HANDLER);
            CatalogSupport.disableAdditionalProperties(
                    def.getConnectorType().getSchema(),
                    "/properties/" + CatalogConstants.CAPABILITY_DATA_SHAPE);

            //
            // Patch
            //

            if (connector.getCustomizers() != null) {
                ImportCustomizer ic = new ImportCustomizer();

                CompilerConfiguration cc = new CompilerConfiguration();
                cc.addCompilationCustomizers(ic);

                ClassLoader cl = Thread.currentThread().getContextClassLoader();

                Binding binding = new Binding();
                binding.setProperty("mapper", JSON_MAPPER);
                binding.setProperty("log", getLog());
                binding.setProperty("connector", connector);
                binding.setProperty("definition", def);
                binding.setProperty("schema", def.getConnectorType().getSchema());

                for (File customizer : connector.getCustomizers()) {
                    if (!Files.exists(customizer.toPath())) {
                        continue;
                    }

                    getLog().info("Customizing: " + connector.getName() + " with customizer " + customizer);

                    new GroovyShell(cl, binding, cc).run(customizer, new String[] {});
                }
            }

            //
            // Revision
            //

            try {
                if (Files.exists(definitionFile)) {
                    JsonNode newSchema = JSON_MAPPER.convertValue(def, ObjectNode.class);
                    JsonNode oldSchema = JSON_MAPPER.readValue(definitionFile.toFile(), JsonNode.class);

                    JsonAssertions.assertThatJson(oldSchema)
                            .when(Option.IGNORING_ARRAY_ORDER)
                            .whenIgnoringPaths(
                                    "$.channels.*.shard_metadata.connector_image",
                                    "$.channels.*.shard_metadata.connector_revision")
                            .withDifferenceListener((difference, context) -> {
                                getLog().info("diff: " + difference.toString());
                                manifest.bump();
                            })
                            .isEqualTo(newSchema);
                }
            } catch (AssertionError e) {
                // ignored, just avoid blowing thing up
            }

            //
            // Images
            //

            if (connector.getChannels() != null) {
                for (var ch : connector.getChannels().entrySet()) {
                    ConnectorDefinition.Metadata metadata = def.getChannels().get(ch.getKey()).getMetadata();

                    String image = String.format("%s/%s/%s-%s:%s",
                            this.containerImageRegistry,
                            this.containerImageOrg,
                            this.containerImagePrefix, this.type,
                            this.containerImageTag);

                    metadata.setConnectorRevision(this.manifest.getRevision());
                    metadata.setConnectorImage(image);
                }
            }

            //
            // As Json
            //

            ObjectNode definition = JSON_MAPPER.convertValue(def, ObjectNode.class);

            if (connector.allowProcessors()) {
                importProcessorSchema(definition);
            }

            //
            // Validate
            //

            if (validate) {
                validateConnector(connector, definition);
            }

            //
            // Write Definition
            //

            Files.createDirectories(definitionPathLocal.toPath());

            getLog().info("Writing connector definition to: " + definitionLocalFile);

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(definitionLocalFile),
                    definition);

            return def;

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void importProcessorSchema(ObjectNode definition) throws IOException {
        ObjectNode dslDefinitions = (ObjectNode) JSON_MAPPER.readTree(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        "schema/camel-yaml-dsl-restricted.json"));
        dslDefinitions = (ObjectNode) dslDefinitions.get("items").get("definitions");
        final ObjectNode schema = (ObjectNode) definition.get("connector_type").get("schema");
        if (!schema.has("$defs")) {
            schema.set("$defs", JSON_MAPPER.createObjectNode());
        }
        final ObjectNode schemaDefs = (ObjectNode) schema.get("$defs");
        dslDefinitions.fields().forEachRemaining((e) -> {
            ((ObjectNode) e.getValue()).findParents("$ref").forEach((refParent) -> {
                String updatedRef = refParent.get("$ref").asText().replace("#/items/definitions", "#/$defs");
                ((ObjectNode) refParent).set("$ref", new TextNode(updatedRef));
            });
            schemaDefs.set(e.getKey(), e.getValue());
        });

        ObjectNode processors = JSON_MAPPER.createObjectNode();
        processors.set("type", new TextNode("array"));
        ObjectNode items = JSON_MAPPER.createObjectNode();
        items.set("$ref", new TextNode("#/$defs/org.apache.camel.model.ProcessorDefinition"));
        processors.set("items", items);
        if (!schema.has("properties")) {
            schema.set("properties", JSON_MAPPER.createObjectNode());
        }
        final ObjectNode schemaProperties = (ObjectNode) schema.get("properties");
        schemaProperties.set("processors", processors);
    }

    private void validateConnector(Connector connector, ObjectNode definition)
            throws MojoExecutionException, MojoFailureException {

        try {
            final Validator.Context context = of(connector);

            for (Validator validator : ServiceLoader.load(Validator.class)) {
                getLog().info("Validating: " + connector.getName() + " with validator " + validator);
                validator.validate(context, definition);
            }

            if (validators != null) {
                ImportCustomizer ic = new ImportCustomizer();

                CompilerConfiguration cc = new CompilerConfiguration();
                cc.addCompilationCustomizers(ic);

                ClassLoader cl = Thread.currentThread().getContextClassLoader();

                Binding binding = new Binding();
                binding.setProperty("context", context);
                binding.setProperty("schema", definition);

                for (File validator : validators) {
                    if (!Files.exists(validator.toPath())) {
                        return;
                    }

                    getLog().info("Validating: " + connector.getName() + " with validator " + validator);
                    new GroovyShell(cl, binding, cc).run(validator, new String[] {});
                }
            }
        } catch (AssertionError | Exception e) {
            throw new MojoFailureException(e);
        }
    }

    public TreeSet<String> dependencies()
            throws MojoExecutionException {

        TreeSet<String> answer = new TreeSet<>();

        try {
            List<Dependency> deps = project.getDependencies().stream()
                    .filter(d -> !"provided".equals(d.getScope()))
                    .sorted(Comparator.comparing(d -> d.getGroupId() + ":" + d.getArtifactId()))
                    .collect(Collectors.toList());

            for (Dependency dep : deps) {
                Artifact artifact = project.getArtifactMap().get(dep.getGroupId() + ":" + dep.getArtifactId());
                MessageDigest digest = DigestUtils.getSha256Digest();
                Path path = artifact.getFile().toPath();
                String gav = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();

                if (dep.getGroupId().startsWith("org.bf2")) {
                    try (JarFile jar = new JarFile(path.toFile())) {
                        List<JarEntry> entries = Collections.list(jar.entries());
                        entries.sort(Comparator.comparing(JarEntry::getName));

                        for (JarEntry entry : entries) {
                            if (entry.isDirectory()) {
                                continue;
                            }
                            if (entry.getName().equals("META-INF/jandex.idx")) {
                                continue;
                            }
                            if (entry.getName().startsWith("META-INF/quarkus-")) {
                                continue;
                            }
                            if (entry.getName().endsWith("git.properties")) {
                                continue;
                            }

                            try (InputStream is = jar.getInputStream(entry)) {
                                DigestUtils.updateDigest(digest, is);
                            }
                        }
                    }
                } else {
                    DigestUtils.updateDigest(digest, gav);
                }

                answer.add(
                        gav + "@sha256:" + DigestUtils.sha256Hex(digest.digest()));
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to build quarkus application", e);
        }

        return answer;
    }

    private Validator.Context of(Connector connector) {
        return new Validator.Context() {
            @Override
            public Path getCatalogPath() {
                return manifestFile;
            }

            @Override
            public Connector getConnector() {
                return connector;
            }

            @Override
            public Log getLog() {
                return getLog();
            }

            @Override
            public Validator.Mode getMode() {
                return mode;
            }
        };
    }
}
