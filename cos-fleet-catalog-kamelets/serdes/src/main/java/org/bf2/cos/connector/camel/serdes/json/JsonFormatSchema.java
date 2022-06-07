package org.bf2.cos.connector.camel.serdes.json;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonFormatSchema implements FormatSchema {
    private final JsonNode schema;

    public JsonFormatSchema(JsonNode schema) {
        this.schema = schema;
    }

    @Override
    public String getSchemaType() {
        return Json.SCHEMA_TYPE;
    }

    public JsonNode getSchema() {
        return schema;
    }
}
