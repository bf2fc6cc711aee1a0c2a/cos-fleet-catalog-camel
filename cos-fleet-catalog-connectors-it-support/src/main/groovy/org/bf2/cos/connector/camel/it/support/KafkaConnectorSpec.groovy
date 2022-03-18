package org.bf2.cos.connector.camel.it.support

import groovy.util.logging.Slf4j
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import org.testcontainers.containers.Network

@Slf4j
abstract class KafkaConnectorSpec extends ConnectorSpecSupport {
    static Network network
    static KafkaContainer kafka

    def setupSpec() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build()

        network = Network.newNetwork()

        kafka = new KafkaContainer()
        kafka.withNetwork(network)
        kafka.start()
    }

    def cleanupSpec() {
        log.info("cleaning up kafka container")
        closeQuietly(kafka)

        log.info("cleaning up network container")
        closeQuietly(network)
    }

    // **********************************
    //
    // Helpers
    //
    // **********************************

    String topic() {
        def topic = UUID.randomUUID().toString()

        kafka.createTopic(topic)

        return topic
    }

    ConnectorContainer connectorContainer(String definition, Map<String, Object> properties) {
        return ConnectorContainer.forDefinition(definition).withProperties(properties).witNetwork(network).build()
    }

    ConnectorContainer.Builder connectorContainer(String definition) {
        return ConnectorContainer.forDefinition(definition).witNetwork(network)
    }
}
