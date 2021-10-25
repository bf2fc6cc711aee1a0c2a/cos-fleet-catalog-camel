package org.bf2.cos.connector.camel.serdes.json;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.jackson.SchemaResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.ObjectHelper;
import org.bf2.cos.connector.camel.serdes.Serdes;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.SchemaGenerator;

import static org.bf2.cos.connector.camel.serdes.SerdesHelper.isPojo;

public class JsonSchemaResolver implements SchemaResolver, Processor {
    private final ConcurrentMap<String, JsonNode> schemes;
    private final SchemaGenerator generator;

    private JsonNode schema;
    private String contentClass;

    public JsonSchemaResolver() {
        this.schemes = new ConcurrentHashMap<>();
        this.generator = Json.generator(Json.MAPPER);
    }

    public String getSchema() {
        if (this.schema != null) {
            try {
                return Json.MAPPER.writeValueAsString(this.schema);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    public void setSchema(String schema) {
        if (ObjectHelper.isNotEmpty(schema)) {
            try {
                this.schema = Json.MAPPER.readTree(schema);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.schema = null;
        }
    }

    public String getContentClass() {
        return contentClass;
    }

    public void setContentClass(String contentClass) {
        if (ObjectHelper.isNotEmpty(contentClass)) {
            this.contentClass = contentClass;
        } else {
            this.contentClass = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object payload = exchange.getMessage().getBody();
        if (payload == null) {
            return;
        }

        JsonNode answer = this.schema;

        if (answer == null) {
            answer = exchange.getProperty(Serdes.CONTENT_SCHEMA, JsonNode.class);
        }

        if (answer == null) {
            String contentClass = exchange.getProperty(Serdes.CONTENT_CLASS, this.contentClass, String.class);
            if (contentClass == null && isPojo(payload.getClass())) {
                contentClass = payload.getClass().getName();
            }
            if (contentClass == null) {
                return;
            }

            answer = this.schemes.computeIfAbsent(contentClass, t -> {
                Resource res = exchange.getContext()
                        .adapt(ExtendedCamelContext.class)
                        .getResourceLoader()
                        .resolveResource("classpath:schemas/" + Json.SCHEMA_TYPE + "/" + t + "." + Json.SCHEMA_TYPE);

                try {
                    if (res.exists()) {
                        try (InputStream is = res.getInputStream()) {
                            if (is != null) {
                                return Json.MAPPER.readTree(is);
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Unable to load Json schema for type: " + t + ", resource: " + res.getLocation(),
                            e);
                }

                try {
                    return this.generator.generateSchema(payload.getClass());
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Unable to compute Json schema for type: " + t,
                            e);
                }
            });
        }

        if (answer != null) {
            exchange.setProperty(Serdes.CONTENT_SCHEMA, answer);
            exchange.setProperty(Serdes.CONTENT_SCHEMA_TYPE, Json.SCHEMA_TYPE);
            exchange.setProperty(Serdes.CONTENT_CLASS, payload.getClass().getName());
        }
    }

    @Override
    public FormatSchema resolve(Exchange exchange) {
        return exchange.getProperty(Serdes.CONTENT_SCHEMA, JsonFormatSchema.class);
    }
}
