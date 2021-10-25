package org.bf2.cos.connector.camel.serdes.json;

import org.apache.avro.Schema;
import org.apache.camel.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.github.fge.avro.MutableTree;
import com.github.fge.avro.translators.AvroTranslators;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.DevNullProcessingReport;
import com.github.fge.jsonschema.core.report.ProcessingReport;

@Converter(generateLoader = true)
public class JsonTypeConverter {
    private static final ProcessingReport REPORT = new DevNullProcessingReport();

    @Converter
    public JsonNode asJsonSchema(AvroSchema schema) throws ProcessingException {
        return asJsonSchema(schema.getAvroSchema());
    }

    @Converter
    public JsonNode asJsonSchema(Schema schema) throws ProcessingException {
        final MutableTree tree = new MutableTree();
        final Schema.Type avroType = schema.getType();

        AvroTranslators.getTranslator(avroType)
                .translate(schema, tree, REPORT);

        return tree.getBaseNode();
    }

    @Converter
    public JsonFormatSchema asJsonFormatSchema(JsonNode schema) {
        return new JsonFormatSchema(schema);
    }

    @Converter
    public JsonFormatSchema asJsonFormatSchema(String schema) {
        try {
            return asJsonFormatSchema(Json.MAPPER.readTree(schema));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
