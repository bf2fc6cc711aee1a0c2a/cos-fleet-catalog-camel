package org.bf2.cos.catalog.camel.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.suport.KameletsCatalog;

import static org.bf2.cos.catalog.camel.maven.suport.KameletsCatalog.type;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractMojo {
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
        final ClassLoader cl = getClassLoader(project);
        final KameletsCatalog catalog = new KameletsCatalog(cl, getLog());

        try (InputStream is = Files.newInputStream(definition)) {
            final ArrayNode root = JSON_MAPPER.createArrayNode();

            for (JsonNode connector : YAML_MAPPER.readValue(is, ArrayNode.class)) {
                final ObjectNode connectorSpec = catalog.kamelet(connector.requiredAt("/spec/connector"));
                final ObjectNode kafkaSpec = catalog.kamelet(connector.requiredAt("/spec/kafka"));
                final JsonNode stepsSpec = connector.requiredAt("/spec/steps");
                final String name = connector.requiredAt("/metadata/name").asText();
                final JsonNode annotations = connector.requiredAt("/metadata/annotations");
                final String version = annotations.required("connector.version").asText();
                final String type = type(connectorSpec);
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
                        .put("connector", KameletsCatalog.name(connectorSpec))
                        .put("kafka", KameletsCatalog.name(kafkaSpec));

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
        final ClassLoader cl = getClassLoader(project);
        final KameletsCatalog catalog = new KameletsCatalog(cl, getLog());

        try (InputStream is = Files.newInputStream(definition)) {
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
                entry.putArray("labels").add(type(connectorSpec));

                //
                // Connector
                //

                var connectorProps = entry.with("json_schema").with("properties").with("connector");
                connectorProps.put("type", "object");
                connectorProps.set("title", connectorSpec.requiredAt("/spec/definition/title"));

                addRequired(
                        connectorSpec,
                        connectorProps.withArray("required"));
                remapProperties(
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
                remapProperties(
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

    private void addRequired(ObjectNode source, ArrayNode target) {
        JsonNode required = source.at("/spec/definition/required");
        if (required.getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode node : required) {
                target.add(node.asText());
            }
        }
    }

    private void remapProperties(ObjectNode source, ObjectNode target) {
        var fields = source.requiredAt("/spec/definition/properties").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            var value = (ObjectNode) field.getValue();
            value.remove("x-descriptors");

            target.set(field.getKey(), value);
        }
    }

    private String normalizeName(String name) {
        String[] items = name.split(":");
        if (items.length == 1) {
            return "camel.apache.org/v1alpha1:Kamelet:" + name;
        }
        if (items.length == 3) {
            return name;
        }

        throw new IllegalArgumentException("Wrong name format: " + name);
    }

    public String extractName(String normalizedName) {
        String[] items = normalizedName.split(":");
        if (items.length == 1) {
            return items[0];
        }
        if (items.length == 3) {
            return items[2];
        }

        throw new IllegalArgumentException("Wrong name format: " + normalizedName);
    }

    private ClassLoader getClassLoader(MavenProject project) {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, KameletsCatalog.class.getClassLoader());
        } catch (Exception e) {
            getLog().debug("Couldn't get the classloader.");
            return KameletsCatalog.class.getClassLoader();
        }
    }
}