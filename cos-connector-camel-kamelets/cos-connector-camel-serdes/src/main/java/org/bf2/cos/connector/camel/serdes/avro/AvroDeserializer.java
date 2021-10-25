package org.bf2.cos.connector.camel.serdes.avro;

import java.nio.charset.StandardCharsets;

import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.BaseDeserializer;
import org.bf2.cos.connector.camel.serdes.Serdes;

import io.apicurio.registry.serde.ParsedSchema;

public class AvroDeserializer extends BaseDeserializer<Schema> {
    public AvroDeserializer() {
        super(Avro.SCHEMA_PARSER);
    }

    @Override
    protected void configureHeaders(Headers headers, ParsedSchema<Schema> schema) {
        headers.add(
                Serdes.CONTENT_SCHEMA,
                schema.getParsedSchema().toString().getBytes(StandardCharsets.UTF_8));
    }
}
