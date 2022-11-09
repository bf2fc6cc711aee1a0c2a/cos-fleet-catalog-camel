package org.bf2.cos.connector.camel.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;

public class ToStringHeaderDeserializer implements KafkaHeaderDeserializer {
    @Override
    public Object deserialize(String key, byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
