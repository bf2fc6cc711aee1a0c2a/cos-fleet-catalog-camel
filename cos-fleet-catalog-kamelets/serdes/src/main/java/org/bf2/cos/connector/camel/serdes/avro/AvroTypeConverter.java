package org.bf2.cos.connector.camel.serdes.avro;

import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;
import org.apache.camel.Converter;

import com.fasterxml.jackson.dataformat.avro.AvroSchema;

@Converter(generateLoader = true)
public class AvroTypeConverter {
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
}
