package org.bf2.cos.connector.camel.serdes.json;

import org.apache.camel.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

@Converter(generateLoader = true)
public class JsonTypeConverter {
    @Converter
    public JsonFormatSchema asJsonFormatSchema(JsonNode schema) {
        return new JsonFormatSchema(schema);
    }

    @Converter
    public JsonFormatSchema asJsonFormatSchema(String schema) {
        try {
            return asJsonFormatSchema(Json.MAPPER.readTree(schema));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
