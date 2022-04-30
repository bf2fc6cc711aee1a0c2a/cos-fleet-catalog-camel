package org.bf2.cos.connector.camel.it.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class KafkaContainer extends GenericContainer<KafkaContainer> {
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_OUTSIDE_PORT = 29092;
    public static final String CONTAINER_ALIAS = "tc-kafka";

    private static final String STARTER_SCRIPT = "/var/lib/redpanda/redpanda.sh";

    public KafkaContainer() {
        super("docker.io/vectorized/redpanda:v21.11.15");

        withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"));
        withCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
        waitingFor(Wait.forLogMessage(".*Started Kafka API server.*", 1));
        withExposedPorts(KAFKA_PORT, KAFKA_OUTSIDE_PORT);
        withNetworkAliases(CONTAINER_ALIAS);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);

        final String addr = String.join(
                ",",
                "OUTSIDE://0.0.0.0:" + KAFKA_PORT,
                "PLAINTEXT://0.0.0.0:" + KAFKA_OUTSIDE_PORT);

        final String advAddr = String.join(
                ",",
                String.format("OUTSIDE://%s:%d", getHost(), getMappedPort(KAFKA_PORT)),
                String.format("PLAINTEXT://%s:%s", CONTAINER_ALIAS, KAFKA_OUTSIDE_PORT));

        String command = "#!/bin/bash\n";
        command += String.join(" ",
                "/usr/bin/rpk",
                "redpanda",
                "start",
                "--check=false",
                "--node-id 0",
                "--smp 1",
                "--memory 1G",
                "--overprovisioned",
                "--reserve-memory 0M",
                "--kafka-addr",
                addr,
                "--advertise-kafka-addr",
                advAddr);

        //noinspection OctalInteger
        copyFileToContainer(
                Transferable.of(command.getBytes(StandardCharsets.UTF_8), 0777),
                STARTER_SCRIPT);
    }

    public String getBootstrapServers() {
        return String.format("PLAINTEXT://%s:%d", getHost(), getMappedPort(KAFKA_PORT));
    }

    public String getOutsideBootstrapServers() {
        return CONTAINER_ALIAS + ":" + KAFKA_OUTSIDE_PORT;
    }

    public RecordMetadata send(String topic, String value) {
        return send(topic, null, value, Map.of());
    }

    public RecordMetadata send(String topic, String value, Map<String, String> headers) {
        return send(topic, null, value, headers);
    }

    public RecordMetadata send(String topic, String key, String value) {
        return send(topic, key, value, Map.of());
    }

    public RecordMetadata send(String topic, String key, String value, Map<String, String> headers) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (var kp = new KafkaProducer<String, String>(config)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

            headers.forEach((k, v) -> {
                record.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
            });

            return kp.send(record).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ConsumerRecords<String, String> poll(String groupId, String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OffsetResetStrategy.EARLIEST.name().toLowerCase(Locale.US));

        try (var kp = new KafkaConsumer<String, String>(config)) {
            kp.subscribe(List.of(topic));

            var answer = kp.poll(Duration.ofSeconds(30));

            kp.commitSync();

            return answer;
        }
    }
}
