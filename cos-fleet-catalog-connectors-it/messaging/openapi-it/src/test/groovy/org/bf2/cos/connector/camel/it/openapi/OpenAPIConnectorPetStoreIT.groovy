package org.bf2.cos.connector.camel.it.openapi

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.WaitStrategies
import org.bf2.cos.connector.camel.it.support.spock.RequiresDefinition
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

@Slf4j
@RequiresDefinition('rest_openapi_sink_0.1.json')
class OpenAPIConnectorPetStoreIT extends KafkaConnectorSpec {
    static final int HTTP_PORT = 8080
    static final String HTTP_SCHEME = 'http'
    static final String HOST = 'tc-petstore'

    static GenericContainer petStore

    @Override
    def setupSpec() {
        petStore = new GenericContainer(DockerImageName.parse('swaggerapi/petstore:latest'))
        petStore.withLogConsumer(logger(HOST))
        petStore.withNetwork(network)
        petStore.withNetworkAliases(HOST)
        petStore.withExposedPorts(HTTP_PORT)
        petStore.waitingFor(WaitStrategies.forHttpEndpoint(HTTP_SCHEME, HTTP_PORT, '/api/swagger.json'))
        petStore.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(petStore)
    }

    @Unroll
    def "openapi petstore sink"() {
        setup:
            def topic = topic()
            def payload = '''{ "name": "bar", "photoUrls": [ "https://foo.bar" ] }'''

            def cnt = connectorContainer('rest_openapi_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': topic,
                'rest_openapi_host': "${HTTP_SCHEME}://${HOST}:${HTTP_PORT}".toString(),
                'rest_openapi_specification': "${HTTP_SCHEME}://${HOST}:${HTTP_PORT}/api/swagger.json".toString(),
                'rest_openapi_operation': 'addPet',
            ])

            cnt.withUserProperty('camel.main.name', 'rest_openapi_sink_0.1')
            cnt.start()
        when:
            kafka.send(topic, payload)

        then:
            def records = kafka.poll(topic)
            records.size() == 1
            records.first().value() == payload

            until(5, TimeUnit.SECONDS) {
                def app =  cnt.request.get('/q/metrics').jsonPath().getMap('application')

                return app['camel.context.exchanges.completed.total;camelContext=rest_openapi_sink_0.1'] == 1
                    && app['camel.context.exchanges.failed.total;camelContext=rest_openapi_sink_0.1'] == 0
            }

        cleanup:
            closeQuietly(cnt)
    }
}
