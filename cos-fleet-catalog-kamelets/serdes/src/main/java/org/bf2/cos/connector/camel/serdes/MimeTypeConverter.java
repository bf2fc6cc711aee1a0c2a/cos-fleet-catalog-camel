package org.bf2.cos.connector.camel.serdes;

import org.apache.camel.Converter;

@Converter(generateLoader = true)
public class MimeTypeConverter {
    @Converter
    public static MimeType toMimeType(String type) {
        return MimeType.of(type);
    }
}
