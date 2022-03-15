package org.bf2.cos.connector.camel.it.support

import groovy.util.logging.Slf4j
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration

@Slf4j
abstract class ConnectorSpec extends Specification {
    @TempDir
    Path tmpDir

    Network network
    KafkaContainer kafka
    ConnectorContainer connector

    def setupSpec() {
        configureRestAssured()
    }

    def setup() {
        this.network = Network.newNetwork()

        this.kafka = new KafkaContainer()
        this.kafka.withNetwork(this.network)

        this.connector = new ConnectorContainer()
        this.connector.withNetwork(network)

        doSetup()

        this.kafka.start()
        this.connector.start()
    }

    def cleanup() {
        doCleanup()

        closeQuietly(this.connector)
        closeQuietly(this.kafka)
        closeQuietly(this.network)
    }

    def doSetup() {
    }

    def doCleanup() {
    }

    RequestSpecification getRequest() {
        return RestAssured.given()
                .baseUri("http://${this.connector.serviceAddress}")
                .port(this.connector.servicePort)
    }

    // **********************************
    //
    // Helpers
    //
    // **********************************

    RecordMetadata sendToKafka(String topic, String value) {
        return sendToKafka(topic, null, value, [:])
    }

    RecordMetadata sendToKafka(String topic, String value, Map<String, String> headers) {
        return sendToKafka(topic, null, value, headers)
    }

    RecordMetadata sendToKafka(String topic, String key, String value) {
        return sendToKafka(topic, key, value, [:])
    }

    RecordMetadata sendToKafka(String topic, String key, String value, Map<String, String> headers) {
        Properties config = new Properties()
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafka.getBootstrapServers())
        config.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString())
        config.put(ProducerConfig.ACKS_CONFIG, "all")
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)

        try (var kp = new KafkaProducer<String, String>(config)) {
            def record = new ProducerRecord<>(topic, key, value.stripMargin().stripIndent())

            headers.each {
                record.headers().add(it.key, it.value.getBytes(StandardCharsets.UTF_8))
            }

            return kp.send(record).get()
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }

    ConsumerRecords<String, String> readFromKafka(String topic) {
        Properties config = new Properties()
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.kafka.getBootstrapServers())
        config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true)
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true)
        config.put(ConsumerConfig.GROUP_ID_CONFIG, this.connector.getContainerId())
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OffsetResetStrategy.EARLIEST.name().toLowerCase(Locale.US))

        try (var kp = new KafkaConsumer<String, String>(config)) {
            kp.subscribe(List.of(topic))

            var answer = kp.poll(Duration.ofSeconds(30))

            kp.commitSync()

            return answer
        }
    }

    void addFileToConnectorContainer(String containerPath, String content) {
        addFileToContainer(connector, containerPath, content)
    }

    void addFileToContainer(GenericContainer<?> container, String containerPath, String content) {
        var fp = PosixFilePermissions.fromString("rw-rw-rw-")

        try {
            Path f = Files.createTempFile(tmpDir, null, null, PosixFilePermissions.asFileAttribute(fp))
            Files.write(f, content.stripMargin().stripIndent().getBytes(StandardCharsets.UTF_8))

            container.addFileSystemBind(
                    f.toAbsolutePath().toString(),
                    containerPath,
                    BindMode.READ_ONLY,
                    SelinuxContext.SHARED)
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }

    static Slf4jLogConsumer logger(String name) {
        new Slf4jLogConsumer(LoggerFactory.getLogger(name))
    }

    static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return
        }

        try {
            closeable.close()
        } catch (Exception e) {
            log.debug('Failed to close {}', closeable, e)
        }
    }

    private static void configureRestAssured() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build()
    }
}
