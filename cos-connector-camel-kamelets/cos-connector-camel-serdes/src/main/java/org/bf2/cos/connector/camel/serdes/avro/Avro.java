package org.bf2.cos.connector.camel.serdes.avro;

import org.apache.avro.Schema;
import org.apache.kafka.common.header.Header;
import org.bf2.cos.connector.camel.serdes.Serdes;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.serde.avro.AvroSchemaParser;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;

public final class Avro {
    public static final String SCHEMA_TYPE = "avsc";
    public static final AvroMapper MAPPER = new AvroMapper();

    public static SchemaParser<Schema, byte[]> SCHEMA_PARSER = new AvroSchemaParser<>(new DefaultAvroDatumProvider<>()) {
        @Override
        public ParsedSchema<Schema> getSchemaFromData(Record<byte[]> record) {
            final KafkaSerdeRecord<byte[]> kr = (KafkaSerdeRecord<byte[]>) record;
            final Header schemaHeader = kr.metadata().getHeaders().lastHeader(Serdes.CONTENT_SCHEMA);
            final Schema schema = parseSchema(schemaHeader.value());

            return new ParsedSchemaImpl<Schema>()
                    .setParsedSchema(schema)
                    .setRawSchema(schemaHeader.value());
        }
    };

    private Avro() {

    }
}
