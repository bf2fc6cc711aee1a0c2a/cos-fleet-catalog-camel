package org.bf2.cos.connector.camel.serdes.json;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.BaseDeserializer;
import org.bf2.cos.connector.camel.serdes.Serdes;

import com.fasterxml.jackson.databind.JsonNode;

import io.apicurio.registry.serde.ParsedSchema;

public class JsonDeserializer extends BaseDeserializer<JsonNode> {
    public JsonDeserializer() {
        super(Json.SCHEMA_PARSER);
    }

    @Override
    protected void configureHeaders(Headers headers, ParsedSchema<JsonNode> schema) {
        headers.add(
                Serdes.CONTENT_SCHEMA,
                schema.getParsedSchema().toString().getBytes(StandardCharsets.UTF_8));
    }
}
