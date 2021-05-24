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
    @Parameter(defaultValue = "${project.build.directory}/static")
    private String staticPath;
    @Parameter
    private List<String> definitions;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        for (String definition : definitions) {
            Path path = Paths.get(definition);
            generateIndex(path);
            generateDefinitions(path);
        }
    }

    private void generateIndex(Path definition) throws MojoExecutionException, MojoFailureException {
        try (InputStream is = Files.newInputStream(definition)) {
            final KameletsCatalog catalog = new KameletsCatalog(getClassLoader(project));
            final ArrayNode root = JSON_MAPPER.createArrayNode();

            for (JsonNode connector : YAML_MAPPER.readValue(is, ArrayNode.class)) {
                final ObjectNode connectorSpec = catalog.kamelet(connector.requiredAt("/spec/connector"));
                final ObjectNode kafkaSpec = catalog.kamelet(connector.requiredAt("/spec/kafka"));
                final JsonNode stepsSpec = connector.requiredAt("/spec/steps");
                final String name = connector.requiredAt("/metadata/name").asText();
                final JsonNode annotations = connector.requiredAt("/metadata/annotations");
                final String version = annotations.required("connector.version").asText();
                final String type = kameletType(connectorSpec);
                final String id = String.format("%s-%s", name, version);

                ObjectNode entry = root.addObject();
                entry.put("id", id);
                entry.put("channel", "stable");
                entry.set("revision", annotations.required("connector.revision"));

                ObjectNode shardMeta = entry.putObject("shard_metadata");
                shardMeta.put("connector_type", type);
                shardMeta.set("connector_image", annotations.required("connector.image"));
                shardMeta.set("meta_image", annotations.required("connector.meta.image"));
                shardMeta.withArray("operators").addObject()
                        .put("type", "camel-k")
                        .set("version", annotations.required("connector.operator.version"));
                shardMeta.with("kamelets")
                        .put("connector", kameletName(connectorSpec))
                        .put("kafka", kameletName(kafkaSpec));

                for (JsonNode step : stepsSpec) {
                    shardMeta.with("kamelets").put(
                            step.required("name").asText().replace("-action", ""),
                            step.required("name").asText());
                }
            }

            //
            // Write
            //

            Path out = Paths.get(staticPath)
                    .resolve("v1")
                    .resolve("kafka-connector-catalog");

            Files.createDirectories(out);

            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(out.resolve("index.json")),
                    root);
        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void generateDefinitions(Path definition) throws MojoExecutionException, MojoFailureException {
        try (InputStream is = Files.newInputStream(definition)) {
            final KameletsCatalog catalog = new KameletsCatalog(getClassLoader(project));

            for (JsonNode connector : YAML_MAPPER.readValue(is, ArrayNode.class)) {
                final ObjectNode connectorSpec = catalog.kamelet(connector.requiredAt("/spec/connector"));
                final ObjectNode kafkaSpec = catalog.kamelet(connector.requiredAt("/spec/kafka"));
                final JsonNode stepsSpec = connector.requiredAt("/spec/steps");
                final String name = connector.requiredAt("/metadata/name").asText();
                final JsonNode annotations = connector.requiredAt("/metadata/annotations");
                final String version = annotations.required("connector.version").asText();
                final String id = String.format("%s-%s", name, version);

                ObjectNode entry = JSON_MAPPER.createObjectNode();
                entry.with("json_schema").put("type", "object");
                entry.put("id", String.format("%s-%s", name, version));
                entry.put("type", "object");
                entry.put("kind", "ConnectorType");
                entry.put("icon_href", "TODO");
                entry.set("name", annotations.required("connector.name"));
                entry.set("description", annotations.required("connector.description"));
                entry.put("version", version);
                entry.putArray("labels").add(kameletType(connectorSpec));

                //
                // Connector
                //

                var connectorProps = entry.with("json_schema").with("properties").with("connector");
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

                var kafkaProps = entry.with("json_schema").with("properties").with("kafka");
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

                if (!stepsSpec.isEmpty()) {
                    final var oneOf = (ArrayNode) entry.with("json_schema").with("properties").with("steps")
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
                // Write
                //

                Path out = Paths.get(staticPath)
                        .resolve("v1")
                        .resolve("kafka-connector-types")
                        .resolve(id);

                Files.createDirectories(out);

                JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                        Files.newBufferedWriter(out.resolve("index.json")),
                        entry);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }
}