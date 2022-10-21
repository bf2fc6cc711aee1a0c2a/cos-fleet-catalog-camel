package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import org.bf2.cos.connector.camel.it.support.ConnectorContainer
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.KafkaContainer
import org.bf2.cos.connector.camel.it.support.SimpleConnectorSpec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorHealthIT extends SimpleConnectorSpec {
    static final int PORT = 8080
    static final String SCHEME = 'http'
    static final String HOST = 'tc-mock'

    static Network network
    static GenericContainer mock

    def setupSpec() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build()

        network = Network.newNetwork()

        mock = new GenericContainer<>(ContainerImages.WIREMOCK.imageName())
        mock.withLogConsumer(logger(HOST))
        mock.withNetwork(KafkaConnectorSpec.network)
        mock.withNetworkAliases(HOST)
        mock.withExposedPorts(PORT)
        mock.waitingFor(Wait.forListeningPort())
        mock.start()
    }


    def cleanupSpec() {
        log.info("cleaning up network container")
        closeQuietly(network)

        log.info("cleaning up http mock")
        closeQuietly(mock)
    }

    def "container image exposes readiness "() {
        setup:
            def topic = UUID.randomUUID().toString()

            def kafka = new KafkaContainer()
            kafka.withNetwork(network)
            kafka.start()
            kafka.createTopic(topic)

            def cnt = connector('http_sink_0.1.json', kafka, topic)
            cnt.start()
        when:
            cnt.start()
        then:
            untilAsserted(10, TimeUnit.SECONDS) {
                def health = cnt.request.get('/q/health/ready')
                def unstructured = health.as(Map.class)

                with(unstructured) {
                    status == 'UP'
                    checks.find {
                        it.name == 'context' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-routes' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-consumers' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-kafka' && it.status == 'UP'
                    }
                }
            }
        cleanup:
            closeQuietly(cnt)
            closeQuietly(kafka)
    }

    def "container image reports readiness down when kafka stops"() {
        setup:
            def topic = UUID.randomUUID().toString()

            def kafka = new KafkaContainer()
            kafka.withNetwork(network)
            kafka.start()
            kafka.createTopic(topic)

            def cnt = connector('http_sink_0.1.json', kafka, topic)
            cnt.start()
        when:
            closeQuietly(kafka)
        then:
            untilAsserted(10, TimeUnit.SECONDS) {
                def health = cnt.request.get('/q/health/ready')
                def unstructured = health.as(Map.class)

                with(unstructured) {
                    status == 'DOWN'
                    checks.find {
                        it.name == 'context' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-routes' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-consumers' && it.status == 'UP'
                    }
                    checks.find {
                        it.name == 'camel-kafka' && it.status == 'DOWN'
                    }
                }
            }
        cleanup:
            closeQuietly(cnt)
            closeQuietly(kafka)
    }

    def connector(String definition, KafkaContainer kafka, String topic) {
        return ConnectorContainer.forDefinition(definition)
            .witNetwork(network)
            .withUserProperty('quarkus.log.category."org.apache.camel.main".level', 'INFO')
            .withUserProperty('quarkus.log.category."org.apache.camel.microprofile.health".level', 'INFO')
            .withUserProperty('camel.main.route-controller-supervise-enabled', 'true')
            .withUserProperty('camel.main.route-controller-back-off-max-attempts', '10')
            .withUserProperty('camel.main.route-controller-unhealthy-on-exhausted', 'true')
            .withUserProperty('camel.health.enabled ', 'true')
            .withUserProperty('camel.health.routes-enabled ', 'true')
            .withUserProperty('camel.health.registry-enabled ', 'true')
            .withProperty('kafka_topic', topic)
            .withProperty('kafka_bootstrap_servers', kafka.outsideBootstrapServers)
            .withProperty('kafka_consumer_group', topic)
            .withProperty('http_method', 'POST')
            .withProperty('http_url', "${SCHEME}://${HOST}:${PORT}/run".toString())
            .build()
    }
}
