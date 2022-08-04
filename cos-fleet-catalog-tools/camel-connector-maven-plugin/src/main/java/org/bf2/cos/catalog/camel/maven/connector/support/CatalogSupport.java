package org.bf2.cos.catalog.camel.maven.connector.support;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.model.ConnectorDefinition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.CaseFormat;

public final class CatalogSupport {
    public static final ObjectMapper YAML_MAPPER = new YAMLMapper()
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
            .configure(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true)
            .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);

    public static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private CatalogSupport() {
    }

    public static void addRequired(boolean groups, Connector.EndpointRef ref, ObjectNode source, ObjectNode schemaRoot) {
        JsonNode required = source.at("/spec/definition/required");
        if (required.getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode node : required) {
                ObjectNode properties = (ObjectNode) source.requiredAt("/spec/definition/properties/" + node.asText());
                ArrayNode descriptors = (ArrayNode) properties.get("x-descriptors");
                ObjectNode target = schemaRoot;

                if (groups) {
                    target = getCamelDescriptors(descriptors)
                            .stream()
                            .filter(pair -> Objects.equals("group", pair.getKey()))
                            .map(pair -> schemaRoot.with(asKey(pair.getValue())))
                            .findFirst()
                            .orElseGet(() -> schemaRoot.with("common"));
                }

                target.withArray("required").add(asKey(ref, node.asText()));
            }
        }
    }

    public static void copyProperties(boolean groups, Connector.EndpointRef ref, ObjectNode source, ObjectNode schemaRoot) {
        var fields = source.at("/spec/definition/properties").fields();
        while (fields.hasNext()) {
            final var field = fields.next();
            final var key = asKey(ref, field.getKey());
            final var value = (ObjectNode) field.getValue();

            // remove json schema extensions from kamelets
            final var descriptors = (ArrayNode) value.remove("x-descriptors");

            ObjectNode target = schemaRoot;

            if (groups) {
                target = getCamelDescriptors(descriptors)
                        .stream()
                        .filter(pair -> Objects.equals("group", pair.getKey()))
                        .map(pair -> schemaRoot.with(asKey(pair.getValue())))
                        .findFirst()
                        .orElseGet(() -> schemaRoot.with("common"));
            }

            var format = value.get("format");
            if (format != null && Objects.equals("password", format.textValue())) {
                var property = target.with("properties").putObject(key);
                property.set("title", value.get("title"));

                if (!groups) {
                    getCamelDescriptors(descriptors).forEach(pair -> property.put("x-" + pair.getKey(), pair.getValue()));
                }

                var oneOf = property.putArray("oneOf");
                oneOf.add(value);
                oneOf.addObject()
                        .put("description", "An opaque reference to the " + key)
                        .put("type", "object")
                        .putObject("properties");
            } else {
                if (!groups) {
                    getCamelDescriptors(descriptors).forEach(pair -> value.put("x-" + pair.getKey(), pair.getValue()));
                }

                target.with("properties").set(key, value);
            }
        }
    }

    public static ClassLoader getClassLoader(MavenProject project) {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, KameletsCatalog.class.getClassLoader());
        } catch (Exception e) {
            return KameletsCatalog.class.getClassLoader();
        }
    }

    public static String kameletType(ObjectNode node) {
        return node.requiredAt("/metadata/labels").get("camel.apache.org/kamelet.type").asText();
    }

    public static String kameletName(ObjectNode node) {
        return node.requiredAt("/metadata/name").asText();
    }

    public static String kameletVersion(ObjectNode node) {
        JsonNode annotations = node.requiredAt("/metadata/annotations");
        JsonNode version = annotations.get("camel.apache.org/kamelet.version");
        if (version == null) {
            version = annotations.get("camel.apache.org/catalog.version");
        }

        if (version == null) {
            return null;
        }

        return version.asText();
    }

    public static String asKey(Connector.EndpointRef ref, String value) {
        if (ref.getPrefix() != null) {
            value = ref.getPrefix().endsWith("_")
                    ? ref.getPrefix() + value
                    : ref.getPrefix() + "_" + value;
        }

        return asKey(value);
    }

    public static String asKey(String value) {
        String answer = CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(value);
        return CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(answer);
    }

    public static List<Pair<String, String>> getCamelDescriptors(ArrayNode descriptors) {
        if (descriptors == null) {
            return Collections.emptyList();
        }

        List<Pair<String, String>> answer = new ArrayList<>();

        for (JsonNode node : descriptors) {
            String descriptor = node.asText();
            if (descriptor.startsWith("urn:camel:")) {
                descriptor = descriptor.substring("urn:camel:".length());

                String[] items = descriptor.split(":");
                if (items.length == 2) {
                    answer.add(Pair.of(items[0], items[1]));
                }
            }
        }

        return answer;
    }

    public static boolean isNullOrEmpty(Connector.DataShape ds) {
        if (ds == null) {
            return true;
        }

        return ds.getDefaultFormat() == null && (ds.getFormats() == null || ds.getFormats().isEmpty());
    }

    public static void computeDataShapes(Connector.DataShapeDefinition ds, ObjectNode adapterSpec) {

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

        if (Objects.equals(CatalogConstants.SOURCE, kameletType(adapterSpec))) {
            // consumes from adapter
            // produces to kafka

            if (isNullOrEmpty(ds.getConsumes())) {
                JsonNode mediaType = adapterSpec.at("/spec/types/out/mediaType");
                if (!mediaType.isMissingNode()) {
                    String format = mediaType.asText();

                    if (ds.getConsumes() == null) {
                        ds.setConsumes(new Connector.DataShape());
                    }

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

            if (isNullOrEmpty(ds.getProduces())) {
                if (!isNullOrEmpty(ds.getConsumes())) {
                    if (ds.getProduces() == null) {
                        ds.setProduces(new Connector.DataShape());
                    }
                    ds.getProduces().setDefaultFormat(ds.getConsumes().getDefaultFormat());
                    ds.getProduces().setFormats(Set.of(ds.getConsumes().getDefaultFormat()));

                    /*
                     *
                     * Don't do data conversion for now
                     *
                     * switch (ds.getConsumes().getDefaultFormat()) {
                     * case "application/json":
                     * case "avro/binary":
                     * ds.getProduces().setFormats(Set.of("application/json", "avro/binary"));
                     * break;
                     * default:
                     * ds.getProduces().setFormats(null);
                     * break;
                     * }
                     */
                }
            }
        } else {
            // consumes from kafka
            // produces to adapter

            if (isNullOrEmpty(ds.getProduces())) {
                JsonNode mediaType = adapterSpec.at("/spec/types/in/mediaType");
                if (!mediaType.isMissingNode()) {
                    String format = mediaType.asText();

                    if (ds.getProduces() == null) {
                        ds.setProduces(new Connector.DataShape());
                    }

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

            if (isNullOrEmpty(ds.getConsumes())) {
                if (!isNullOrEmpty(ds.getProduces())) {
                    if (ds.getConsumes() == null) {
                        ds.setConsumes(new Connector.DataShape());
                    }

                    ds.getConsumes().setDefaultFormat(ds.getProduces().getDefaultFormat());
                    ds.getConsumes().setFormats(Set.of(ds.getProduces().getDefaultFormat()));

                    /*
                     *
                     * Don't do data conversion for now
                     *
                     * switch (ds.getProduces().getDefaultFormat()) {
                     * case "application/json":
                     * case "avro/binary":
                     * ds.getConsumes().setFormats(Set.of("application/json", "avro/binary"));
                     * break;
                     * default:
                     * ds.getConsumes().setFormats(null);
                     * break;
                     * }
                     */
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
    }

    public static void computeErrorHandler(ConnectorDefinition def, Connector connector) {
        def.getConnectorType().getCapabilities().add(CatalogConstants.CAPABILITY_ERROR_HANDLER);

        final var oneOf = (ArrayNode) def.getConnectorType().getSchema()
                .with("properties")
                .with(CatalogConstants.CAPABILITY_ERROR_HANDLER)
                .put("type", "object")
                .withArray("oneOf");

        if (connector.getErrorHandler().getDefaultStrategy() != null) {
            switch (connector.getErrorHandler().getDefaultStrategy()) {
                case LOG:
                case STOP:
                    def.getConnectorType().getSchema()
                            .with("properties")
                            .with(CatalogConstants.CAPABILITY_ERROR_HANDLER)
                            .with("default")
                            .putObject(connector.getErrorHandler().getDefaultStrategy().name().toLowerCase(Locale.US));
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported default strategy: " + connector.getErrorHandler().getDefaultStrategy());
            }
        }

        connector.getErrorHandler().getStrategies().stream().sorted().forEach(strategy -> {
            final ObjectNode eh = oneOf.addObject();

            String strategyName = strategy.name().toLowerCase(Locale.US);

            eh.put("type", "object");
            eh.put("additionalProperties", false);
            eh.withArray("required").add(strategyName);

            withPropertyRef(eh, CatalogConstants.CAPABILITY_ERROR_HANDLER, strategyName);

            withDefinition(def, CatalogConstants.CAPABILITY_ERROR_HANDLER, strategyName, d -> {
                d.put("type", "object");
                d.put("additionalProperties", false);

                if (strategy == Connector.ErrorHandler.Strategy.DEAD_LETTER_QUEUE) {
                    d.putArray("required")
                            .add("topic");
                    d.with("properties")
                            .with("topic")
                            .put("type", "string")
                            .put("title", "Dead Letter Topic Name")
                            .put("description",
                                    "The name of the Kafka topic that serves as a destination for messages which were not processed correctly due to an error.");
                }
            });
        });
    }

    public static void dataShape(Connector.DataShape dataShape, ConnectorDefinition definition, Connector.DataShape.Type type) {
        if (dataShape == null) {
            return;
        }
        if (Objects.equals(dataShape.getDefaultFormat(), "application/x-java-object")) {
            return;
        }
        if (dataShape.getFormats() == null || dataShape.getFormats().isEmpty()) {
            return;
        }

        final String id = type.getId();

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

            ObjectNode formatNode = d.with("properties").with("format");
            formatNode.put("type", "string");

            switch (type) {
                case CONSUMES:
                    formatNode.put("description", "The format of the data that Kafka sends to the sink connector.");
                    break;
                case PRODUCES:
                    formatNode.put("description", "The format of the data that the source connector sends to Kafka.");
                    break;
            }

            if (dataShape.getDefaultFormat() != null) {
                formatNode.put("default",
                        dataShape.getDefaultFormat());
            } else if (dataShape.getFormats().size() == 1) {
                formatNode.put("default",
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
                    format -> formatNode.withArray("enum").add(format));
        });
    }

    public static ObjectNode withDefinition(ConnectorDefinition definition, String group, String name,
            Consumer<ObjectNode> consumer) {
        ObjectNode answer = definition.getConnectorType().getSchema().with("$defs").with(group).with(name);
        consumer.accept(answer);
        return answer;
    }

    public static ObjectNode withProperty(JsonNode root, String propertyName, Consumer<ObjectNode> consumer) {
        ObjectNode answer = root.with("properties").with(propertyName);
        consumer.accept(answer);
        return answer;
    }

    public static ObjectNode withPropertyRef(JsonNode root, String group, String propertyName) {
        return withProperty(root, propertyName, d -> {
            d.put(
                    "$ref",
                    "#/$defs/" + group + "/" + propertyName);
        });
    }

    public static void disableAdditionalProperties(JsonNode root, String path) {
        JsonNode node = root.at(path);

        if (node.isMissingNode()) {
            return;
        }
        if (!node.isEmpty()) {
            return;
        }
        if (!node.isObject()) {
            return;
        }

        ((ObjectNode) node).put("type", "object");
        ((ObjectNode) node).put("additionalProperties", false);
    }
}
