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

    @Override
    ConnectorContainer connectorContainer(String name) {
        def answer = super.connectorContainer(name)
        answer.setNetwork(network)

        return answer
    }

    ConnectorContainer connectorContainer(String name, ContainerFile... files) {
        def answer = connectorContainer(name)

        for (ContainerFile file : files) {
            answer.withFile(file.path, file.body.stripLeading().stripTrailing())
        }

        return answer
    }

    ConnectorContainer connectorContainer(String name, String route) {
        def answer = connectorContainer(name)

        return connectorContainer(name, DEFAULT_APPLICATION_PROPERTIES, route)
    }

    ConnectorContainer connectorContainer(String name, String properties, String route) {
        def answer = connectorContainer(name)

        answer.withFile(DEFAULT_APPLICATION_PROPERTIES_LOCATION, properties.stripLeading().stripTrailing())
        answer.withFile(DEFAULT_ROUTE_LOCATION, route.stripLeading().stripTrailing())

        return answer
    }
}
