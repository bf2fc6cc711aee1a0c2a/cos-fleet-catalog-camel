package org.bf2.cos.connector.camel.serdes.schema;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.Serdes;

import io.apicurio.registry.serde.DefaultSchemaResolver;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.ParsedSchemaImpl;
import io.apicurio.registry.serde.SchemaLookupResult;

public class InflightSchemaResolver<S> extends DefaultSchemaResolver<S, byte[]> {
    @Override
    public SchemaLookupResult<S> resolveSchema(String topic, Headers headers, byte[] data, ParsedSchema<S> unused) {
        final Header schemaHeader = headers.lastHeader(Serdes.CONTENT_SCHEMA);
        final S schema = schemaParser.parseSchema(schemaHeader.value());

        final ParsedSchema<S> parsedSchema = new ParsedSchemaImpl<S>()
                .setParsedSchema(schema)
                .setRawSchema(schemaHeader.value());

        return super.resolveSchema(topic, headers, data, parsedSchema);
    }
}
