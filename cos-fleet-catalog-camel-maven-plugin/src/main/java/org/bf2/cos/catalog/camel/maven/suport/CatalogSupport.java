package org.bf2.cos.catalog.camel.maven.suport;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.maven.project.MavenProject;

public final class CatalogSupport {
    public static final ObjectMapper YAML_MAPPER = new YAMLMapper()
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
            .configure(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true)
            .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);

    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private CatalogSupport() {
    }

    public static void addRequired(ObjectNode source, ArrayNode target) {
        JsonNode required = source.at("/spec/definition/required");
        if (required.getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode node : required) {
                target.add(node.asText());
            }
        }
    }

    public static void copyProperties(ObjectNode source, ObjectNode target) {
        var fields = source.requiredAt("/spec/definition/properties").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            var value = (ObjectNode) field.getValue();
            value.remove("x-descriptors");

            var format = value.get("format");
            if (format != null && Objects.equals("password", format.textValue())) {
                var property = target.putObject(field.getKey());
                property.set("title", value.get("title"));

                var oneOf = property.putArray("oneOf");
                oneOf.add(value);
                oneOf.addObject()
                        .put("description", "An opaque reference to the " + field.getKey())
                        .put("type", "object")
                        .putObject("properties");
            } else {
                target.set(field.getKey(), value);
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
}