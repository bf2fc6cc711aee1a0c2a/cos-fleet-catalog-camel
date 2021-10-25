package org.bf2.cos.connector.camel.serdes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.SchemaResolver;

public abstract class BaseSerializer<S> extends AbstractKafkaSerializer<S, byte[]> {
    private final SchemaParser<S> parser;

    protected BaseSerializer(SchemaParser<S> parser, SchemaResolver<S, byte[]> resolver) {
        super(resolver);

        this.parser = parser;
    }

    @Override
    public SchemaParser<S> schemaParser() {
        return parser;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(new BaseConfig(configs), isKey);
    }

    @Override
    protected void serializeData(ParsedSchema<S> schema, byte[] data, OutputStream out) throws IOException {
        serializeData(null, schema, data, out);
    }

    @Override
    protected void serializeData(Headers headers, ParsedSchema<S> schema, byte[] data, OutputStream out) throws IOException {
        if (headers != null) {
            configureHeaders(headers);
        }

        out.write(data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }

        if (headers == null) {
            throw new IllegalStateException("headers are required");
        }
        if (headers.lastHeader(Serdes.CONTENT_SCHEMA) == null) {
            throw new IllegalStateException("schema header is required");
        }

        try {
            return super.serialize(topic, headers, data);
        } finally {
            headers.remove(Serdes.CONTENT_SCHEMA);
            headers.remove(Serdes.CONTENT_CLASS);
            headers.remove(Serdes.CONTENT_SCHEMA_TYPE);
        }
    }

    protected abstract void configureHeaders(Headers headers);
}
