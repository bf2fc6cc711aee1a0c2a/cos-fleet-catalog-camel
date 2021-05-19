package org.bf2.cos.catalog.camel.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
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

import static org.bf2.cos.catalog.camel.maven.suport.KameletsCatalog.type;

@Mojo(name = "generate-catalog", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateCatalogMojo extends AbstractMojo {

    private final Predicate<Map.Entry<String, ObjectNode>> filter;
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "camel.apache.org/v1alpha1:Kamelet:kafka-source")
    private String managedKafkaSourceKamelet;
    @Parameter(defaultValue = "camel.apache.org/v1alpha1:Kamelet:kafka-sink")
    private String managedKafkaSinkKamelet;
    @Parameter(defaultValue = "${project.build.directory}/static")
    private String staticPath;
    @Parameter
    private List<String> actions;
    @Parameter
    private List<String> bannedKamelets;

    public GenerateCatalogMojo() {
        this.filter = k -> {
            if (k.getKey().equals(managedKafkaSinkKamelet) || k.getKey().equals(managedKafkaSourceKamelet)) {
                return false;
            }

            return bannedKamelets == null
                    || (!bannedKamelets.contains(k.getKey()) && bannedKamelets.stream().noneMatch(b -> k.getKey().matches(b)));
        };
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        generateIndex();
        generateFiles();
    }

    private void generateIndex() throws MojoExecutionException, MojoFailureException {
        final KameletsCatalog catalog = new KameletsCatalog(project, getLog());
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode root = mapper.createArrayNode();

        catalog.kamelets()
                .filter(filter)
                .forEach(k -> {
                    final String connectorName = k.getValue().requiredAt("/metadata/name").asText();
                    final String connectorType = type(k.getValue());
                    final String connectorVersion = "1.0";
                    final String connectorRevision = "1";
                    final String connectorId = String.format("%s-%s", connectorName, connectorVersion);
                    final String connectorImage = String.format(
                            "quay.io/cos/%s:%s.%s",
                            connectorName,
                            connectorVersion,
                            connectorRevision);

                    ObjectNode entry = root.addObject();
                    entry.put("id", connectorId);
                    entry.put("channel", "stable");
                    entry.put("revision", connectorRevision);

                    ObjectNode meta = entry.putObject("shard_metadata");
                    // TODO: externalize
                    meta.put("meta_image", "quay.io/lburgazzoli/cms:latest");
                    meta.put("connector_image", connectorImage);
                    meta.put("connector_type", connectorType);

                    meta.withArray("operators")
                            .addObject()
                            .put("type", "camel-k")
                            // TODO: externalize
                            .put("versions", "[1.0.0,2.0.0)");

                    meta.with("kamelets").put("connector", k.getKey());

                    switch (connectorType) {
                        case "source": {
                            meta.with("kamelets").put("kafka", normalizeName(managedKafkaSinkKamelet));
                            break;
                        }
                        case "sink": {
                            meta.with("kamelets").put("kafka", normalizeName(managedKafkaSourceKamelet));
                            break;
                        }
                    }

                    if (actions != null) {
                        catalog.actions()
                                .filter(a -> actions.contains(a.getKey()))
                                .forEach(a -> {
                                    meta.with("kamelets").put(
                                            a.getValue().requiredAt("/metadata/name").asText().replace("-action", ""),
                                            String.format("%s:%s:%s",
                                                    a.getValue().get("apiVersion").asText(),
                                                    a.getValue().get("kind").asText(),
                                                    a.getValue().requiredAt("/metadata/name").asText()));
                                });
                    }
                });

        try {
            Path out = Paths.get(staticPath)
                    .resolve("v1")
                    .resolve("kafka-connector-catalog");

            Files.createDirectories(out);

            mapper.writerWithDefaultPrettyPrinter().writeValue(
                    Files.newBufferedWriter(out.resolve("index.json")),
                    root);

        } catch (IOException e) {
            throw new MojoExecutionException("", e);
        }
    }

    private void generateFiles() throws MojoExecutionException, MojoFailureException {
        final KameletsCatalog catalog = new KameletsCatalog(project, getLog());
        final ObjectMapper mapper = new ObjectMapper();

        catalog.kamelets()
                .filter(filter)
                .forEach(k -> {
                    final String connectorName = k.getValue().requiredAt("/metadata/name").asText();
                    final String connectorType = type(k.getValue());
                    final String connectorVersion = "1.0";
                    final String connectorId = String.format("%s-%s", connectorName, connectorVersion);

                    ObjectNode entry = mapper.createObjectNode();
                    entry.with("json_schema").put("type", "object");
                    entry.put("id", connectorId);
                    entry.put("type", "object");
                    entry.put("kind", "ConnectorType");
                    //entry.put("href", connectorTypesPath + "/" + connectorId);
                    // TODO: externalize
                    entry.put("icon_href", "TODO");
                    entry.set("name", k.getValue().requiredAt("/spec/definition/title"));
                    // TODO: externalize
                    entry.put("version", connectorVersion);
                    entry.set("description", k.getValue().requiredAt("/spec/definition/description"));
                    entry.putArray("labels").add(connectorType);

                    var connector = entry.with("json_schema").with("properties").with("connector");
                    connector.put("type", "object");
                    connector.set("title", k.getValue().requiredAt("/spec/definition/title"));

                    addRequired(
                            k.getValue(),
                            connector.withArray("required"),
                            null);
                    remapProperties(
                            k.getValue(),
                            connector.with("properties"),
                            null,
                            n -> {
                            });

                    switch (connectorType) {
                        case "source": {
                            final String kafkaKamelet = normalizeName(managedKafkaSinkKamelet);
                            final ObjectNode kafka = catalog.getKamelets().get(kafkaKamelet);
                            final ObjectNode props = entry.with("json_schema").with("properties").with("kafka");
                            props.put("type", "object");
                            props.set("title", kafka.requiredAt("/spec/definition/title"));

                            addRequired(
                                    kafka,
                                    props.withArray("required"),
                                    null);
                            remapProperties(
                                    kafka,
                                    props.with("properties"),
                                    null,
                                    n -> {
                                    });
                            break;
                        }
                        case "sink": {
                            final String kafkaKamelet = normalizeName(managedKafkaSourceKamelet);
                            final ObjectNode kafka = catalog.getKamelets().get(kafkaKamelet);
                            final ObjectNode props = entry.with("json_schema").with("properties").with("kafka");
                            props.put("type", "object");
                            props.set("title", kafka.requiredAt("/spec/definition/title"));

                            addRequired(
                                    kafka,
                                    props.withArray("required"),
                                    "kafka.");
                            remapProperties(
                                    kafka,
                                    props.with("properties"),
                                    "kafka.",
                                    n -> {
                                    });
                            break;
                        }
                    }

                    if (actions != null) {
                        catalog.actions()
                                .filter(a -> actions.contains(a.getKey()))
                                .forEach(a -> {
                                    String actionName = extractName(a.getKey());

                                    entry.with("json_schema").with("definitions").with("actions").set(
                                            actionName,
                                            a.getValue().requiredAt("/spec/definition"));

                                    entry.with("json_schema").with("properties").with("steps")
                                            .put("type", "array")
                                            .with("items")
                                            .put("type", "object")
                                            .put("maxProperties", 1)
                                            .with("properties")
                                            .with(actionName)
                                            .put("$ref", "#/definitions/actions/" + actionName);
                                });
                    }

                    try {
                        Path out = Paths.get(staticPath)
                                .resolve("v1")
                                .resolve("kafka-connector-types")
                                .resolve(connectorId);

                        Files.createDirectories(out);

                        mapper.writerWithDefaultPrettyPrinter().writeValue(
                                Files.newBufferedWriter(out.resolve("index.json")),
                                entry);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void addRequired(ObjectNode source, ArrayNode target, String prefix) {
        JsonNode required = source.at("/spec/definition/required");
        if (required.getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode node : required) {
                if (prefix != null) {
                    target.add(prefix + node.asText());
                } else {
                    target.add(node.asText());
                }
            }
        }
    }

    private void remapProperties(ObjectNode source, ObjectNode target, String prefix, Consumer<ObjectNode> customizer) {
        var fields = source.requiredAt("/spec/definition/properties").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            var value = (ObjectNode) field.getValue();
            value.remove("x-descriptors");

            customizer.accept((ObjectNode) value);

            if (prefix != null) {
                target.set(prefix + field.getKey(), value);
            } else {
                target.set(field.getKey(), value);
            }
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
}