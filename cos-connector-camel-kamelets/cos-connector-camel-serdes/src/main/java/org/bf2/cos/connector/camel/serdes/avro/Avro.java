package org.bf2.cos.connector.camel.serdes.avro;

import org.apache.avro.Schema;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;

import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.avro.AvroSchemaParser;

public final class Avro {
    public static final String SCHEMA_TYPE = "avsc";
    public static final AvroMapper MAPPER = new AvroMapper();
    public static SchemaParser<Schema> SCHEMA_PARSER = new AvroSchemaParser();

    private Avro() {
    }
}
