package org.bf2.cos.connector.camel.it.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import groovy.util.logging.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
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

@Slf4j
public class KafkaContainer extends RedPandaKafkaContainer {
    @Override
    public String getBootstrapServers() {
        return super.getBootstrapServers();
    }

    @Override
    public String getOutsideBootstrapServers() {
        return super.getOutsideBootstrapServers();
    }

    public void createTopic(String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());

        try (AdminClient admin = KafkaAdminClient.create(config)) {
            if (!admin.listTopics().names().get().contains(topic)) {
                admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1)));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

        //createTopic(topic);

        try (var kp = new KafkaProducer<String, String>(config)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

            headers.forEach((k, v) -> {
                record.headers().add(k, v.getBytes(StandardCharsets.UTF_8));
            });

            logger().info("Sending message to Kafka | Topic {} | Key: {} | Value: {}}", topic, key, value);
            RecordMetadata recordMetadata = kp.send(record).get();
            logger().info("Message sent to Kafka | Topic {} | Key: {} | Value: {}}", topic, key, value);
            return recordMetadata;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public KafkaConsumer<String, String> consumer(String groupId, String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OffsetResetStrategy.EARLIEST.name().toLowerCase(Locale.US));

        KafkaConsumer<String, String> kp = new KafkaConsumer<>(config);
        kp.subscribe(List.of(topic));

        return kp;
    }

    public ConsumerRecords<String, String> poll(String groupId, String topic) {
        try (var kp = consumer(groupId, topic)) {
            logger().info("Polling message from Kafka | GroupId {} | Topic: {}", groupId, topic);
            var answer = kp.poll(Duration.ofSeconds(30));
            kp.commitSync();
            logger().info("Message polled from Kafka | GroupId {} | Topic: {}", groupId, topic);
            return answer;
        }
    }

    public ConsumerRecords<String, String> poll(KafkaConsumer<String, String> consumer) {
        var answer = consumer.poll(Duration.ofSeconds(30));
        consumer.commitSync();
        return answer;
    }
}
