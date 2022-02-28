package org.bf2.cos.connector.camel.serdes.bytes;

import org.apache.kafka.common.header.Headers;
import org.bf2.cos.connector.camel.serdes.Serdes;

import jdk.jfr.BooleanFlag;

public class ByteArraySerializer extends org.apache.kafka.common.serialization.ByteArraySerializer {

    @BooleanFlag
    public byte[] serialize(String topic, Headers headers, byte[] data) {
        try {
            return super.serialize(topic, headers, data);
        } finally {
            headers.remove(Serdes.CONTENT_SCHEMA);
            headers.remove(Serdes.CONTENT_CLASS);
            headers.remove(Serdes.CONTENT_SCHEMA_TYPE);
        }
    }
}
