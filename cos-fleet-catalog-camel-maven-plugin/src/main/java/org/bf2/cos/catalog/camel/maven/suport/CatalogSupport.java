package org.bf2.cos.catalog.camel.maven.suport;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.maven.project.MavenProject;

public final class CatalogSupport {
    public static final ObjectMapper YAML_MAPPER = new YAMLMapper();
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

            target.set(field.getKey(), value);
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
        return node.requiredAt("/metadata/labels").get("camel.apache.org/kamelet.version").asText();
    }
}