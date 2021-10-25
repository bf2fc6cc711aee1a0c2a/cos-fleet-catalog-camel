package org.bf2.cos.catalog.camel.maven.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.model.ConnectorDefinition;
import org.bf2.cos.catalog.camel.maven.connector.support.Annotation;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogConstants;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.support.KameletsCatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static java.util.Optional.ofNullable;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.JSON_MAPPER;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.addRequired;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.asKey;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.copyProperties;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.kameletType;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractConnectorMojo {

    @Parameter(defaultValue = "false", property = "cos.catalog.skip")
    private boolean skip = false;

    @Parameter(defaultValue = "false", property = "cos.connector.groups")
    private boolean groups = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/connectors")
    private String outputPath;
    @Parameter
    private List<Annotation> annotations;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping generate-catalog");
        }

        try {
            final KameletsCatalog catalog = KameletsCatalog.get(project, getLog());

            for (Connector connector : getConnectors()) {
                generateDefinitions(catalog, connector);
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private void generateDefinitions(KameletsCatalog catalog, Connector connector)
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
            final ObjectNode adapterSpec = catalog.kamelet(
                    adapter.getName(),
                    adapter.getVersion());
            final ObjectNode kafkaSpec = catalog.kamelet(
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
                def.getConnectorType().getCapabilities().add(CatalogConstants.CAPABILITY_PROCESSORS);

                final var oneOf = (ArrayNode) def.getConnectorType().getSchema()
                        .with("properties")
                        .with(CatalogConstants.CAPABILITY_PROCESSORS)
                        .put("type", "array")
                        .with("items")
                        .withArray("oneOf");

                for (Connector.ActionRef step : connector.getActions()) {
                    String sanitizedName = step.getName();
                    sanitizedName = StringUtils.removeStart(sanitizedName, "cos-");
                    sanitizedName = StringUtils.removeEnd(sanitizedName, "-action");

                    final String stepName = asKey(sanitizedName);
                    final ObjectNode stepSchema = oneOf.addObject();
                    final JsonNode kameletSpec = catalog.kamelet(step.getName(), step.getVersion());

                    def.getConnectorType().getSchema()
                            .with("$defs")
                            .with(CatalogConstants.CAPABILITY_PROCESSORS)
                            .set(stepName, kameletSpec.requiredAt("/spec/definition"));

                    if (step.getMetadata() != null) {
                        ObjectNode meta = def.getConnectorType().getSchema()
                                .with("$defs")
                                .with(CatalogConstants.CAPABILITY_PROCESSORS)
                                .with(stepName)
                                .with("x-metadata");

                        step.getMetadata().forEach(meta::put);
                    }

                    def.getConnectorType().getSchema()
                            .with("$defs")
                            .with(CatalogConstants.CAPABILITY_PROCESSORS)
                            .with(stepName)
                            .put("type", "object");

                    stepSchema.put("type", "object");
                    stepSchema.withArray("required").add(stepName);
                    stepSchema.with("properties").with(stepName).put(
                            "$ref",
                            "#/$defs/" + CatalogConstants.CAPABILITY_PROCESSORS + "/" + stepName);
                }
            }

            //
            // DataShape
            //

            var ds = connector.getDataShape();
            if (ds == null) {
                ds = new Connector.DataShapeDefinition();
            }

            if ("source".equals(kameletType(adapterSpec))) {
                // consumes from adapter
                // produces to kafka

                if (ds.getConsumes() == null) {
                    JsonNode mediaType = adapterSpec.at("/spec/types/out/mediaType");
                    if (!mediaType.isMissingNode()) {
                        String format = mediaType.asText();

                        ds.setConsumes(new Connector.DataShape());
                        ds.getConsumes().setDefaultFormat(format);

                        switch (format) {
                            case "application/json":
                            case "avro/binary":
                                ds.getConsumes().setFormats(null);
                                break;
                            default:
                                ds.getConsumes().setFormats(Set.of(format));
                                break;
                        }
                    }
                }

                if (ds.getProduces() == null) {
                    ds.setProduces(new Connector.DataShape());

                    if (ds.getConsumes() != null) {
                        ds.getProduces().setDefaultFormat(ds.getConsumes().getDefaultFormat());

                        switch (ds.getConsumes().getDefaultFormat()) {
                            case "application/json":
                            case "avro/binary":
                                ds.getProduces().setFormats(Set.of("application/json", "avro/binary"));
                                break;
                            default:
                                ds.getProduces().setFormats(null);
                                break;
                        }
                    }
                }
            } else {
                // consumes from kafka
                // produces to adapter

                if (ds.getProduces() == null) {
                    JsonNode mediaType = adapterSpec.at("/spec/types/in/mediaType");
                    if (!mediaType.isMissingNode()) {
                        String format = mediaType.asText();

                        ds.setProduces(new Connector.DataShape());
                        ds.getProduces().setDefaultFormat(format);

                        switch (format) {
                            case "application/json":
                            case "avro/binary":
                                ds.getProduces().setFormats(null);
                                break;
                            default:
                                ds.getProduces().setFormats(Set.of(format));
                                break;
                        }
                    }
                }

                if (ds.getConsumes() == null) {
                    ds.setConsumes(new Connector.DataShape());

                    if (ds.getProduces() != null) {
                        ds.getConsumes().setDefaultFormat(ds.getProduces().getDefaultFormat());

                        switch (ds.getProduces().getDefaultFormat()) {
                            case "application/json":
                            case "avro/binary":
                                ds.getConsumes().setFormats(Set.of("application/json", "avro/binary"));
                                break;
                            default:
                                ds.getConsumes().setFormats(null);
                                break;
                        }
                    }
                }
            }

            if (ds.getConsumes() != null &&
                    ds.getConsumes().getDefaultFormat() == null &&
                    ds.getConsumes().getFormats() != null
                    && ds.getConsumes().getFormats().size() == 1) {
                ds.getConsumes().setDefaultFormat(ds.getConsumes().getFormats().iterator().next());
            }

            if (ds.getProduces() != null &&
                    ds.getProduces().getDefaultFormat() == null &&
                    ds.getProduces().getFormats() != null
                    && ds.getProduces().getFormats().size() == 1) {
                ds.getProduces().setDefaultFormat(ds.getProduces().getFormats().iterator().next());
            }

            dataShape(ds.getConsumes(), def, "consumes");
            dataShape(ds.getProduces(), def, "produces");

            //
            // ErrorHandler
            //

            if (connector.getErrorHandler() != null && connector.getErrorHandler().getStrategies() != null) {
                def.getConnectorType().getCapabilities().add(CatalogConstants.CAPABILITY_ERROR_HANDLER);

                final var oneOf = (ArrayNode) def.getConnectorType().getSchema()
                        .with("properties")
                        .with(CatalogConstants.CAPABILITY_ERROR_HANDLER)
                        .put("type", "object")
                        .withArray("oneOf");

                for (String strategy : connector.getErrorHandler().getStrategies()) {
                    final ObjectNode eh = oneOf.addObject();

                    String strategyName = strategy.toLowerCase(Locale.US);

                    eh.put("type", "object");
                    eh.put("additionalProperties", false);
                    eh.withArray("required").add(strategyName);

                    withPropertyRef(eh, CatalogConstants.CAPABILITY_ERROR_HANDLER, strategyName);

                    withDefinition(def, CatalogConstants.CAPABILITY_ERROR_HANDLER, strategyName, d -> {
                        d.put("type", "object");
                        d.put("additionalProperties", false);

                        if ("DEAD_LETTER_QUEUE".equals(strategy)) {
                            d.putArray("required")
                                    .add("topic");
                            d.with("properties")
                                    .with("topic")
                                    .put("type", "string")
                                    .put("title", "Dead Letter Topic Name")
                                    .put("description", "The name of the Kafka topic used as dead letter queue");
                        }
                    });
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

                    String image = ch.getValue().getImage();

                    if (image == null) {
                        image = String.format("%s/%s/%s:%s",
                                project.getProperties().getProperty("cos.connector.container.repository"),
                                project.getProperties().getProperty("cos.connector.container.organization"),
                                name,
                                ch.getValue().getRevision());
                    }

                    metadata.setConnectorRevision(ch.getValue().getRevision());
                    metadata.setConnectorType(type);
                    metadata.setConnectorImage(image);

                    metadata.getOperators().add(new ConnectorDefinition.Operator(
                            ch.getValue().getOperatorType(),
                            ch.getValue().getOperatorVersion()));

                    metadata.getKamelets().getAdapter().setName(adapter.getName());
                    metadata.getKamelets().getAdapter().setPrefix(adapter.getPrefix());

                    metadata.getKamelets().getKafka().setName(kafka.getName());
                    metadata.getKamelets().getKafka().setPrefix(kafka.getPrefix());

                    if (ds.getConsumes() != null) {
                        metadata.setConsumes(ds.getConsumes().getDefaultFormat());
                    }
                    if (ds.getProduces() != null) {
                        metadata.setProduces(ds.getProduces().getDefaultFormat());
                    }

                    if (annotations != null) {
                        for (Annotation annotation : annotations) {
                            metadata.getAnnotations().put(annotation.getName(), annotation.getValue());
                        }
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
                }
            }

            //
            // Write
            //

            Path out = Paths.get(outputPath);
            Path file = out.resolve(id + ".json");

            Files.createDirectories(out);

            getLog().info("Writing connector to: " + file);

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(file),
                    def);

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void dataShape(Connector.DataShape dataShape, ConnectorDefinition definition, String id) {
        if (dataShape == null) {
            return;
        }
        if (Objects.equals(dataShape.getDefaultFormat(), "application/x-java-object")) {
            return;
        }
        if (dataShape.getFormats() == null || dataShape.getFormats().isEmpty()) {
            return;
        }

        definition.getConnectorType().getCapabilities().add(CatalogConstants.CAPABILITY_DATA_SHAPE);

        ObjectNode ds = definition.getConnectorType().getSchema()
                .with("properties")
                .with(CatalogConstants.CAPABILITY_DATA_SHAPE);

        ds.put("type", "object");
        ds.put("additionalProperties", false);

        ds.with("properties")
                .with(id)
                .put("$ref", "#/$defs/" + CatalogConstants.CAPABILITY_DATA_SHAPE + "/" + id);

        withDefinition(definition, CatalogConstants.CAPABILITY_DATA_SHAPE, id, d -> {
            d.put("type", "object");
            d.put("additionalProperties", false);
            d.putArray("required").add("format");
            d.with("properties").with("format").put("type", "string");

            if (dataShape.getDefaultFormat() != null) {
                d.with("properties").with("format").put(
                        "default",
                        dataShape.getDefaultFormat());
            } else if (dataShape.getFormats().size() == 1) {
                d.with("properties").with("format").put(
                        "default",
                        dataShape.getFormats().iterator().next());
            }

            switch (dataShape.getSchemaStrategy()) {
                case NONE:
                    break;
                case OPTIONAL:
                    d.with("properties").with("schema").put("type", "string");
                    break;
                case REQUIRED:
                    d.with("properties").with("schema").put("type", "string");
                    d.withArray("required").add("schema");
                    break;
            }

            dataShape.getFormats().stream().sorted().forEach(
                    format -> d.with("properties").with("format").withArray("enum").add(format));
        });
    }

    private ObjectNode withDefinition(ConnectorDefinition definition, String group, String name,
            Consumer<ObjectNode> consumer) {
        ObjectNode answer = definition.getConnectorType().getSchema().with("$defs").with(group).with(name);
        consumer.accept(answer);
        return answer;
    }

    private ObjectNode withProperty(JsonNode root, String propertyName, Consumer<ObjectNode> consumer) {
        ObjectNode answer = root.with("properties").with(propertyName);
        consumer.accept(answer);
        return answer;
    }

    private ObjectNode withPropertyRef(JsonNode root, String group, String propertyName) {
        return withProperty(root, propertyName, d -> {
            d.put(
                    "$ref",
                    "#/$defs/" + group + "/" + propertyName);
        });
    }
}