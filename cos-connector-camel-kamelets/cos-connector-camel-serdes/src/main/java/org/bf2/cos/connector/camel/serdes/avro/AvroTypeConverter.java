package org.bf2.cos.connector.camel.serdes.avro;

import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;
import org.apache.camel.Converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.DevNullProcessingReport;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.core.tree.CanonicalSchemaTree;
import com.github.fge.jsonschema.core.tree.SchemaTree;
import com.github.fge.jsonschema.core.tree.key.SchemaKey;
import com.github.fge.jsonschema.core.util.ValueHolder;
import com.github.fge.jsonschema2avro.AvroWriterProcessor;

@Converter(generateLoader = true)
public class AvroTypeConverter {
    private static final AvroWriterProcessor PROCESSOR = new AvroWriterProcessor();
    private static final ProcessingReport REPORT = new DevNullProcessingReport();

    @Converter
    public static Schema toSchema(String raw) {
        return new Schema.Parser().parse(raw);
    }

    @Converter
    public static AvroSchema toAvroSchema(String raw) {
        return new AvroSchema(toSchema(raw));
    }

    @Converter
    public static Schema toSchema(InputStream raw) throws IOException {
        return new Schema.Parser().parse(raw);
    }

    @Converter
    public static AvroSchema toAvroSchema(InputStream raw) throws IOException {
        return new AvroSchema(toSchema(raw));
    }

    @Converter
    public static String toString(AvroSchema schema) {
        return schema.getAvroSchema().toString();
    }

    @Converter
    public static Schema toSchema(JsonNode node) throws ProcessingException {
        final SchemaTree tree = new CanonicalSchemaTree(SchemaKey.anonymousKey(), node);
        final ValueHolder<SchemaTree> input = ValueHolder.hold("schema", tree);

        return PROCESSOR.process(REPORT, input).getValue();
    }

    @Converter
    public static AvroSchema toAvroSchema(JsonNode node) throws ProcessingException {
        return new AvroSchema(toSchema(node));
    }
}
