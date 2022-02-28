package org.bf2.cos.connector.camel.serdes.bytes;

import org.apache.kafka.common.header.Headers;

public class ByteArrayDeserializer extends org.apache.kafka.common.serialization.ByteArrayDeserializer {
    @Override
    public byte[] deserialize(String topic, Headers headers, byte[] data) {
        return super.deserialize(topic, headers, data);
    }
}
