package org.bf2.cos.connector.camel.kafka;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ReflectionHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient;

public class KafkaConsumersHealthCheck extends AbstractHealthCheck {
    public static final String DATA_ERROR_MESSAGE = "error.message";

    private final KafkaConsumer<?, ?> client;
    private final Properties configuration;
    private final Field networkClientField;

    public KafkaConsumersHealthCheck(String id, KafkaConsumer<?, ?> client, Properties configuration) {
        super("custom-camel-kafka-health-check", id);
        this.client = client;
        this.configuration = configuration;

        Field nhField = null;
        try {
            nhField = client.getClass().getDeclaredField("client");
        } catch (Exception e) {
            // empty
        }

        this.networkClientField = nhField;

    }

    @Override
    public boolean isReadiness() {
        return true;
    }

    @Override
    public boolean isLiveness() {
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        try {
            boolean down = false;

            if (client == null) {
                builder.detail(DATA_ERROR_MESSAGE, "KafkaConsumer has not been created");
                down = true;
            } else if (this.networkClientField != null) {
                ConsumerNetworkClient nc = (ConsumerNetworkClient) ReflectionHelper.getField(this.networkClientField,
                        this.client);
                if (!nc.hasReadyNodes(System.currentTimeMillis())) {
                    builder.detail(DATA_ERROR_MESSAGE, "KafkaConsumer is not ready");
                    down = true;
                }
            }

            if (down) {
                builder.detail("bootstrap.servers", configuration.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));

                String cid = configuration.getProperty(ConsumerConfig.CLIENT_ID_CONFIG);
                if (cid != null) {
                    builder.detail("client.id", cid);
                }
                String gid = configuration.getProperty(ConsumerConfig.GROUP_ID_CONFIG);
                if (gid != null) {
                    builder.detail("group.id", gid);
                }

                builder.down();
            } else {
                builder.up();
            }
        } catch (Throwable e) {
            builder.up();
        }
    }
}