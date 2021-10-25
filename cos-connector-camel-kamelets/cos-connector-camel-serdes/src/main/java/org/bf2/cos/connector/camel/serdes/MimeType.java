package org.bf2.cos.connector.camel.serdes;

import java.util.Objects;

public enum MimeType {
    JSON("application/json"),
    AVRO("avro/binary"),
    TEXT("text/plain"),
    JAVA_OBJECT("application/x-java-object"),
    STRUCT("application/x-struct");

    private static final MimeType[] VALUES = values();
    private final String type;

    MimeType(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }

    public static MimeType of(String type) {
        for (MimeType mt : VALUES) {
            if (Objects.equals(type, mt.type)) {
                return mt;
            }
        }

        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
