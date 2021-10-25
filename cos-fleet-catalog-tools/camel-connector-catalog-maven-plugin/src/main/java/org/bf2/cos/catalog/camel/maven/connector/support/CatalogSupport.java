package org.bf2.cos.catalog.camel.maven.connector.support;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
}