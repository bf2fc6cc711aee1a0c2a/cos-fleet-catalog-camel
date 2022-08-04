package org.bf2.cos.connector.camel.kafka;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.kafka.serde.KafkaHeaderSerializer;

import java.nio.ByteBuffer;

/**
 * This is a workaround until it gets available upstream
 * https://issues.apache.org/jira/browse/CAMEL-18380
 */
public class DefaultKafkaHeaderSerializer implements KafkaHeaderSerializer, CamelContextAware {
    private CamelContext camelContext;

    @Override
    public byte[] serialize(final String key, final Object value) {

        if (value instanceof String) {
            return ((String) value).getBytes();
        } else if (value instanceof Long) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong((Long) value);
            return buffer.array();
        } else if (value instanceof Integer) {
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.putInt((Integer) value);
            return buffer.array();
        } else if (value instanceof Double) {
            ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
            buffer.putDouble((Double) value);
            return buffer.array();
        } else if (value instanceof Boolean) {
            return value.toString().getBytes();
        } else if (value instanceof byte[]) {
            return (byte[]) value;
        }
        return camelContext.getTypeConverter().convertTo(byte[].class, value);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
