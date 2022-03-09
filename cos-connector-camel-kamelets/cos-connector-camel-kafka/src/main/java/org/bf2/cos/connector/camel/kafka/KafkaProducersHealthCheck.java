package org.bf2.cos.connector.camel.kafka;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ReflectionHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.internals.Sender;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

public class KafkaProducersHealthCheck extends AbstractHealthCheck {
    public static final String DATA_ERROR_MESSAGE = "error.message";

    private final KafkaProducer<?, ?> client;
    private final Properties configuration;
    private final Field senderField;
    private final Field networkClientField;

    public KafkaProducersHealthCheck(String id, KafkaProducer<?, ?> client, Properties configuration) {
        super("custom-camel-kafka-health-check", id);
        this.client = client;
        this.configuration = configuration;

        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Field sh = null;
        try {
            sh = KafkaProducer.class.getDeclaredField("sender");
        } catch (Exception e) {
            // empty
        }

        Field nh = null;
        try {
            nh = Sender.class.getDeclaredField("client");
        } catch (Exception e) {
            // empty
        }

        this.senderField = sh;
        this.networkClientField = nh;

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
            } else if (this.senderField != null && this.networkClientField != null) {
                Sender sender = (Sender) ReflectionHelper.getField(this.senderField, this.client);
                ConsumerNetworkClient nc = (ConsumerNetworkClient) ReflectionHelper.getField(this.networkClientField, sender);
                if (!nc.hasReadyNodes(System.currentTimeMillis())) {
                    builder.detail(DATA_ERROR_MESSAGE, "KafkaConsumer is not ready");
                    down = true;
                }
            } else {
                builder.detail(DATA_ERROR_MESSAGE, "KafkaConsumer has not been created");
                down = true;
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