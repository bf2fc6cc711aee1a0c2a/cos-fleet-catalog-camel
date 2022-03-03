package org.bf2.cos.connector.camel.serdes.json;

import java.io.IOException;

import org.apache.kafka.common.header.Header;
import org.bf2.cos.connector.camel.serdes.Serdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.google.gson.annotations.SerializedName;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import io.apicurio.registry.types.ArtifactType;

public final class Json {
    public static final String SCHEMA_TYPE = "json";
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static SchemaParser<JsonNode, byte[]> SCHEMA_PARSER = new SchemaParser<>() {
        @Override
        public ArtifactType artifactType() {
            return ArtifactType.JSON;
        }

        @Override
        public JsonNode parseSchema(byte[] rawSchema) {
            try {
                return MAPPER.readTree(rawSchema);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ParsedSchema<JsonNode> getSchemaFromData(Record<byte[]> record) {
            final KafkaSerdeRecord<byte[]> kr = (KafkaSerdeRecord<byte[]>) record;
            final Header schemaHeader = kr.metadata().getHeaders().lastHeader(Serdes.CONTENT_SCHEMA);
            final JsonNode schema = parseSchema(schemaHeader.value());

            return new ParsedSchemaImpl<JsonNode>()
                    .setParsedSchema(schema)
                    .setRawSchema(schemaHeader.value());
        }

        @Override
        public boolean supportsExtractSchemaFromData() {
            return true;
        }
    };

    private Json() {
    }

    public static SchemaGenerator generator(ObjectMapper mapper) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                mapper,
                SchemaVersion.DRAFT_2019_09,
                OptionPreset.PLAIN_JSON);

        configBuilder.forFields()
                .withPropertyNameOverrideResolver(field -> {
                    SerializedName sn = field.getAnnotationConsideringFieldAndGetter(SerializedName.class);
                    if (sn != null) {
                        return sn.value();
                    }

                    return null;
                });

        SchemaGeneratorConfig config = configBuilder.build();

        return new SchemaGenerator(config);
    }
}
