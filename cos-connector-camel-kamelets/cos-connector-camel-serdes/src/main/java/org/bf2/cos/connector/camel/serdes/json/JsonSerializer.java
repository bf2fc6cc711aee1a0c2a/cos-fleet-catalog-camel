package org.bf2.cos.connector.camel.serdes.json;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.BaseSerializer;
import org.bf2.cos.connector.camel.serdes.schema.InflightSchemaResolver;

import com.fasterxml.jackson.databind.JsonNode;

import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;

public class JsonSerializer extends BaseSerializer<JsonNode> {
    private MessageTypeSerdeHeaders serdeHeaders;

    public JsonSerializer() {
        super(Json.SCHEMA_PARSER, new InflightSchemaResolver<>());
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);

        serdeHeaders = new MessageTypeSerdeHeaders(new HashMap<>(configs), isKey);
    }

    @Override
    protected void configureHeaders(Headers headers) {
        serdeHeaders.addMessageTypeHeader(headers, byte[].class.getName());
    }
}
