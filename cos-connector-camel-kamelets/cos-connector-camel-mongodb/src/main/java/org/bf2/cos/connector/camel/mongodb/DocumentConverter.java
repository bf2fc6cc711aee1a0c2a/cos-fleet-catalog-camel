package org.bf2.cos.connector.camel.mongodb;

import java.nio.charset.StandardCharsets;

import org.apache.camel.Converter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.json.JsonReader;

@Converter(generateLoader = true)
public class DocumentConverter {
    private static final DocumentCodec CODEC = new DocumentCodec();
    private static final DecoderContext CONTEXT = DecoderContext.builder().build();

    @Converter
    public static Document toDocument(byte[] data) {
        final String in = new String(data, StandardCharsets.UTF_8);
        final Document answer;

        if (isBson(data)) {
            JsonReader reader = new JsonReader(in);
            answer = CODEC.decode(reader, CONTEXT);
        } else {
            answer = Document.parse(in);
        }

        return answer;
    }

    private static boolean isBson(byte[] input) {
        int i = 0;
        while (i < input.length) {
            if (input[i] == '{') {
                return false;
            } else if (!Character.isWhitespace(input[i])) {
                return true;
            }

            i++;
        }
        return true;
    }
}
