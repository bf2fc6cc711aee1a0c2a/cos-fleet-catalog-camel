package org.bf2.cos.connector.camel.serdes;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.serde.AbstractKafkaSerializer;

public abstract class BaseSerializer<S> extends AbstractKafkaSerializer<S, byte[]> {
    private final SchemaParser<S, byte[]> parser;

    protected BaseSerializer(SchemaParser<S, byte[]> parser) {
        super(new DefaultSchemaResolver<>());

        this.parser = parser;
    }

    @Override
    public SchemaParser<S, byte[]> schemaParser() {
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
