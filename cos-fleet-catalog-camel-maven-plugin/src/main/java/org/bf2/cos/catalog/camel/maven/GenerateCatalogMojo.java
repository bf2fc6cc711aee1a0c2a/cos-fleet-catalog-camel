package org.bf2.cos.catalog.camel.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.suport.KameletsCatalog;

import static java.util.Optional.ofNullable;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.JSON_MAPPER;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.addRequired;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.copyProperties;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.getClassLoader;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletName;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletType;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractMojo {
    /**
     * Skips the execution of this mojo
     */
    @Parameter(defaultValue = "false", property = "quarkus.build.skip")
    private boolean skip = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.directory}")
    private String outputPath;
    @Parameter
    private List<Connector> connectors;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping generate-catalog");
        }

        try {
            final KameletsCatalog catalog = new KameletsCatalog(getClassLoader(project));

            if (connectors != null) {
                for (Connector connector : connectors) {
                    generateDefinitions(catalog, connector);
                }
            }

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void generateDefinitions(KameletsCatalog catalog, Connector connector)
            throws MojoExecutionException, MojoFailureException {

        try {
            final ObjectNode connectorSpec = catalog.kamelet(
                    connector.getAdapter().getName(),
                    connector.getAdapter().getVersion());
            final ObjectNode kafkaSpec = catalog.kamelet(
                    connector.getKafka().getName(),
                    connector.getKafka().getVersion());

            final String version = ofNullable(connector.getVersion()).orElseGet(project::getVersion);
            final String name = ofNullable(connector.getName()).orElseGet(project::getArtifactId);
            final String title = ofNullable(connector.getTitle()).orElseGet(project::getName);
            final String description = ofNullable(connector.getDescription()).orElseGet(project::getDescription);
            final String type = kameletType(connectorSpec);
            final String id = name.replace("-", "_");

            ObjectNode root = JSON_MAPPER.createObjectNode();
            ObjectNode connectorType = root.putObject("connector_type");
            connectorType.with("json_schema").put("type", "object");
            connectorType.put("id", id);
            connectorType.put("kind", "ConnectorType");
            connectorType.put("icon_href", "TODO");
            connectorType.put("name", title);
            connectorType.put("description", description);
            connectorType.put("version", version);
            connectorType.putArray("labels").add(kameletType(connectorSpec));

            //
            // Connector
            //

            var connectorProps = connectorType.with("json_schema").with("properties").with("connector");
            connectorProps.put("type", "object");
            connectorProps.set("title", connectorSpec.requiredAt("/spec/definition/title"));

            addRequired(
                    connectorSpec,
                    connectorProps.withArray("required"));
            copyProperties(
                    connectorSpec,
                    connectorProps.with("properties"));

            //
            // Kafka
            //

            var kafkaProps = connectorType.with("json_schema").with("properties").with("kafka");
            kafkaProps.put("type", "object");
            kafkaProps.set("title", kafkaSpec.requiredAt("/spec/definition/title"));

            addRequired(
                    kafkaSpec,
                    kafkaProps.withArray("required"));
            copyProperties(
                    kafkaSpec,
                    kafkaProps.with("properties"));

            //
            // Steps
            //

            if (connector.getSteps() != null) {
                final var oneOf = (ArrayNode) connectorType.with("json_schema").with("properties").with("steps")
                        .put("type", "array")
                        .with("items")
                        .withArray("oneOf");

                for (Connector.KameletRef step : connector.getSteps()) {
                    final String stepName = step.getName().replace("-action", "");
                    final ObjectNode stepSchema = oneOf.addObject();
                    final JsonNode kameletSpec = catalog.kamelet(step.getName(), step.getVersion());

                    stepSchema.put("type", "object");
                    stepSchema.withArray("required").add(stepName);
                    stepSchema.with("properties").set(stepName, kameletSpec.requiredAt("/spec/definition"));
                    stepSchema.with("properties").with(stepName).put("type", "object");
                }
            }

            //
            // channels
            //

            if (connector.getChannels() != null) {
                for (var ch : connector.getChannels().entrySet()) {

                    // add channel to the connector definition
                    connectorType.withArray("channels").add(ch.getKey());

                    ObjectNode channel = root.with("channels").with(ch.getKey());
                    String image = ch.getValue().getImage();

                    if (image == null) {
                        image = String.format("%s/%s/%s:%s",
                                project.getProperties().getProperty("cos.connector.container.repository"),
                                project.getProperties().getProperty("cos.connector.container.organization"),
                                name,
                                ch.getValue().getRevision());
                    }

                    ObjectNode shardMeta = channel.putObject("shard_metadata");
                    shardMeta.put("connector_revision", ch.getValue().getRevision());
                    shardMeta.put("connector_type", type);
                    shardMeta.put("connector_image", image);
                    shardMeta.withArray("operators").addObject()
                            .put("type", ch.getValue().getOperatorType())
                            .put("version", ch.getValue().getOperatorVersion());
                    shardMeta.with("kamelets")
                            .put("connector", kameletName(connectorSpec))
                            .put("kafka", kameletName(kafkaSpec));

                    if (connector.getSteps() != null) {
                        for (Connector.KameletRef step : connector.getSteps()) {
                            shardMeta.with("kamelets").put(
                                    step.getName().replace("-action", ""),
                                    step.getName());
                        }
                    }
                }
            }

            //
            // Write
            //

            Path out = Paths.get(outputPath);
            Files.createDirectories(out);

            getLog().info("Writing connector to: " + out.resolve(id + ".json"));

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(out.resolve(id + ".json")),
                    root);
        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }
}