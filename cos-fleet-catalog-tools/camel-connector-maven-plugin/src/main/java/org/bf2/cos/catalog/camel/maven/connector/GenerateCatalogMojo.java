package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
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
import org.bf2.cos.catalog.camel.maven.connector.support.Catalog;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogConstants;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;
import org.bf2.cos.catalog.camel.maven.connector.support.MojoSupport;
import org.bf2.cos.catalog.camel.maven.connector.validator.Validator;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;

import com.fasterxml.jackson.databind.node.ObjectNode;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import static java.util.Optional.ofNullable;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.JSON_MAPPER;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.addRequired;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.asKey;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.computeActions;
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

    @Parameter
    private List<File> validators;
    @Parameter(defaultValue = "FAIL", property = "cos.catalog.validation.mode")
    private Validator.Mode mode;
    @Parameter(required = true)
    private Catalog catalog;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping generate-catalog");
        }

        try {
            final KameletsCatalog kameletsCatalog = KameletsCatalog.get(project, getLog());
            final List<Connector> connectorList = MojoSupport.inject(session, defaults, connectors);

            cleanup(connectorList);

            for (Connector connector : connectorList) {
                generateDefinitions(kameletsCatalog, connector);
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private void cleanup(List<Connector> connectorList)
            throws MojoExecutionException, MojoFailureException {

        final Path manifests = Path.of(catalog.getManifestsPath());

        if (Files.exists(manifests)) {
            try (Stream<Path> files = Files.list(manifests)) {
                for (Path file : files.collect(Collectors.toList())) {
                    getLog().info("" + file);

                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    if (file.getFileName().toString().equals(type + ".json")) {
                        continue;
                    }
                    if (!file.getFileName().toString().endsWith(".json")) {
                        continue;
                    }

                    boolean valid = false;
                    for (Connector connector : connectorList) {
                        String name = ofNullable(connector.getName()).orElseGet(project::getArtifactId);
                        String id = name.replace("-", "_") + ".json";

                        if (id.equals(file.getFileName().toString())) {
                            valid = true;
                            break;
                        }
                    }

                    if (!valid) {
                        getLog().warn("Deleting " + file + " as it does not match any known connector in this module");
                        Files.delete(file);
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e);
            }
        }

    }

    private void generateDefinitions(KameletsCatalog kamelets, Connector connector)
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
            // Steps
            //

            if (connector.getActions() != null) {
                computeActions(def, connector, kamelets);
            }

            //
            // DataShape
            //

            var ds = connector.getDataShape();
            if (ds == null) {
                ds = new Connector.DataShapeDefinition();
            }

            computeDataShapes(ds, adapterSpec);

            dataShape(ds.getConsumes(), def, "consumes");
            dataShape(ds.getProduces(), def, "produces");

            //
            // ErrorHandler
            //

            if (connector.getErrorHandler() != null && connector.getErrorHandler().getStrategies() != null) {
                computeErrorHandler(def, connector);
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

                    String image = ch.getValue().getImage();

                    if (image == null) {
                        image = String.format("%s/%s/%s:%s",
                                project.getProperties().getProperty("cos.connector.container.repository"),
                                project.getProperties().getProperty("cos.connector.container.organization"),
                                name,
                                ch.getValue().getRevision());
                    }

                    metadata.setConnectorRevision(Integer.parseInt(ch.getValue().getRevision()));
                    metadata.setConnectorType(type);
                    metadata.setConnectorImage(image);

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

                    if (connector.getActions() != null) {
                        for (Connector.ActionRef step : connector.getActions()) {
                            String sanitizedName = step.getName();
                            sanitizedName = StringUtils.removeStart(sanitizedName, "cos-");
                            sanitizedName = StringUtils.removeEnd(sanitizedName, "-action");

                            metadata.getKamelets().getProcessors().put(
                                    asKey(sanitizedName),
                                    step.getName());
                        }
                    }

                    if (connector.getErrorHandler() != null && connector.getErrorHandler().getDefaultStrategy() != null) {
                        metadata.setErrorHandlerStrategy(
                                connector.getErrorHandler().getDefaultStrategy().name().toLowerCase(Locale.US));
                    }
                }
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
            // As Json
            //

            ObjectNode definition = JSON_MAPPER.convertValue(def, ObjectNode.class);

            //
            // Validate
            //

            if (validate) {
                validateConnector(connector, definition);
            }

            //
            // Write
            //

            Path out = Paths.get(catalog.getManifestsPath());
            Path file = out.resolve(id + ".json");

            Files.createDirectories(out);

            getLog().info("Writing connector definition manifest to: " + file);

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(file),
                    definition);

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
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

    private Validator.Context of(Connector connector) {
        return new Validator.Context() {
            @Override
            public Catalog getCatalog() {
                return catalog;
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