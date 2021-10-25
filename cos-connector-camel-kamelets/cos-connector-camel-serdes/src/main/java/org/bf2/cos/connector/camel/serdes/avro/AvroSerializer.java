package org.bf2.cos.connector.camel.serdes.avro;

import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.BaseSerializer;
import org.bf2.cos.connector.camel.serdes.schema.InflightSchemaResolver;

import io.apicurio.registry.serde.avro.AvroEncoding;
import io.apicurio.registry.serde.avro.AvroSerdeHeaders;

public class AvroSerializer extends BaseSerializer<Schema> {
    private final AvroSerdeHeaders avroHeaders = new AvroSerdeHeaders(false);

    public AvroSerializer() {
        super(Avro.SCHEMA_PARSER, new InflightSchemaResolver<>());
    }

    @Override
    protected void configureHeaders(Headers headers) {
        avroHeaders.addEncodingHeader(headers, AvroEncoding.BINARY.name());
    }
}
