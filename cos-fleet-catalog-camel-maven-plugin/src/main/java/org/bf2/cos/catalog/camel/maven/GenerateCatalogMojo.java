package org.bf2.cos.catalog.camel.maven;

import java.io.IOException;
import java.io.InputStream;
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

import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.JSON_MAPPER;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.YAML_MAPPER;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.addRequired;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.copyProperties;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.getClassLoader;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletName;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletType;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractMojo {
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.directory}")
    private String outputPath;
    @Parameter
    private List<String> definitions;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final KameletsCatalog catalog = new KameletsCatalog(getClassLoader(project));

            for (String definition : definitions) {
                Path path = Paths.get(definition);
                generateDefinitions(catalog, path);
            }

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void generateDefinitions(KameletsCatalog catalog, Path definition)
            throws MojoExecutionException, MojoFailureException {

        try (InputStream is = Files.newInputStream(definition)) {
            for (JsonNode connector : YAML_MAPPER.readValue(is, ArrayNode.class)) {
                final ObjectNode connectorSpec = catalog.kamelet(connector.requiredAt("/spec/connector"));
                final ObjectNode kafkaSpec = catalog.kamelet(connector.requiredAt("/spec/kafka"));
                final JsonNode channelsSpec = connector.requiredAt("/spec/channels");
                final JsonNode stepsSpec = connector.at("/spec/steps");
                final String name = connector.requiredAt("/metadata/name").asText();
                final JsonNode annotations = connector.requiredAt("/metadata/annotations");
                final String version = annotations.required("connector.version").asText();
                final String type = kameletType(connectorSpec);
                final String id = String.format("%s_%s", name, version).replace("-", "_");

                ObjectNode root = JSON_MAPPER.createObjectNode();
                ObjectNode connectorType = root.putObject("connector_type");
                connectorType.with("json_schema").put("type", "object");
                connectorType.put("id", id);
                connectorType.put("type", "object");
                connectorType.put("kind", "ConnectorType");
                connectorType.put("icon_href", "TODO");
                connectorType.set("name", annotations.required("connector.name"));
                connectorType.set("description", annotations.required("connector.description"));
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

                if (!stepsSpec.isMissingNode() && !stepsSpec.isEmpty()) {
                    final var oneOf = (ArrayNode) connectorType.with("json_schema").with("properties").with("steps")
                            .put("type", "array")
                            .with("items")
                            .withArray("oneOf");

                    for (JsonNode step : stepsSpec) {
                        final String stepName = step.required("name").asText().replace("-action", "");
                        final ObjectNode stepSchema = oneOf.addObject();

                        stepSchema.put("type", "object");
                        stepSchema.withArray("required").add(stepName);
                        stepSchema.with("properties").set(stepName, catalog.kamelet(step).requiredAt("/spec/definition"));
                        stepSchema.with("properties").with(stepName).put("type", "object");
                    }
                }

                //
                // channels
                //

                if (!channelsSpec.isMissingNode() && !channelsSpec.isEmpty()) {
                    ObjectNode channels = root.putObject("channels");

                    var it = channelsSpec.fields();
                    while (it.hasNext()) {
                        var entry = it.next();

                        connectorType.withArray("channels").add(entry.getKey());

                        JsonNode channelAnnotations = entry.getValue().requiredAt("/metadata");

                        ObjectNode channel = channels.putObject(entry.getKey());
                        channel.put("revision", channelAnnotations.required("connector.revision").asLong());

                        ObjectNode shardMeta = channel.putObject("shard_metadata");
                        shardMeta.put("connector_type", type);
                        shardMeta.set("connector_image", channelAnnotations.required("connector.image"));
                        shardMeta.withArray("operators").addObject()
                                .put("type", "camel-connector-operator")
                                .set("version", channelAnnotations.required("connector.operator.version"));
                        shardMeta.with("kamelets")
                                .put("connector", kameletName(connectorSpec))
                                .put("kafka", kameletName(kafkaSpec));

                        if (!stepsSpec.isMissingNode() && !stepsSpec.isEmpty()) {
                            for (JsonNode step : stepsSpec) {
                                shardMeta.with("kamelets").put(
                                        step.required("name").asText().replace("-action", ""),
                                        step.required("name").asText());
                            }
                        }
                    }
                }

                //
                // Write
                //

                Path out = Paths.get(outputPath);
                Files.createDirectories(out);

                JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                        Files.newBufferedWriter(out.resolve(id + ".json")),
                        root);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }
}