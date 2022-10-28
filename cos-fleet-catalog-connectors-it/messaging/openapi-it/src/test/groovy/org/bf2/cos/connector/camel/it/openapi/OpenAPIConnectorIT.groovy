package org.bf2.cos.connector.camel.it.openapi

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.WaitStrategies
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.SelinuxContext
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.verify

@Slf4j
class OpenAPIConnectorIT extends KafkaConnectorSpec {
    static final int HTTP_PORT = 8080
    static final int HTTPS_PORT = 8443
    static final String HTTP_SCHEME = 'http'
    static final String HTTPS_SCHEME = 'https'
    static final String HOST = 'tc-mock'
    static final String JKS_PASS = 'password'
    static final String JKS_PATH = '/etc/wm/cert.jks'
    static final String JKS_CLASSPATH = '/ssl/cert.jks'

    static GenericContainer mock

    @Override
    def setupSpec() {
        mock = ContainerImages.WIREMOCK.container()
        mock.withLogConsumer(logger(HOST))
        mock.withNetwork(network)
        mock.withNetworkAliases(HOST)
        mock.withExposedPorts(HTTP_PORT, HTTPS_PORT)
        mock.waitingFor(WaitStrategies.forHttpEndpoint(HTTP_SCHEME, HTTP_PORT, '/__admin/mappings'))
        mock.withClasspathResourceMapping(JKS_CLASSPATH, JKS_PATH, BindMode.READ_ONLY, SelinuxContext.SHARED)

        mock.withCommand(
            "--port", "${HTTP_PORT}",
            "--https-port", "${HTTPS_PORT}",
            "--https-keystore", "${JKS_PATH}",
            "--keystore-password", "${JKS_PASS}")

        mock.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(mock)
    }

    @Unroll
    def "openapi sink"(String scheme, int port) {
        setup:
            def topic = topic()
            def payload = '''{ "name": "bar", "photoUrls"; [ "https://foo.bar" ] }'''

            WireMock.configureFor(scheme, mock.getHost(), mock.getMappedPort(port))

            // swagger spec
            def swaggerPath = urlPathEqualTo("/api/swagger.json")
            def swaggerResponse = ok().withBody(spec())
            def swaggerRequest = get(swaggerPath)

            WireMock.stubFor(swaggerRequest.willReturn(swaggerResponse))

            // rest endpoint
            def addPetPath = urlPathEqualTo("/v2/pet")
            def addPetResponse = ok()
            def addPetRequest = post(addPetPath)
                    .withHeader('Content-Type', equalTo('application/json'))

            WireMock.stubFor(addPetRequest.willReturn(addPetResponse))

            def cnt = connectorContainer('rest_openapi_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': topic,
                'rest_openapi_host': "${scheme}://${HOST}:${port}".toString(),
                'rest_openapi_specification': "${scheme}://${HOST}:${port}/api/swagger.json".toString(),
                'rest_openapi_operation': 'addPet',
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)

        then:
            def records = kafka.poll(topic)
            records.size() == 1
            records.first().value() == payload

            untilAsserted(5, TimeUnit.SECONDS) {
                verify(1, getRequestedFor(swaggerPath))
                verify(1, postRequestedFor(addPetPath))
            }

            assert WireMock.findUnmatchedRequests().isEmpty()

        cleanup:
            closeQuietly(cnt)

        where:
            scheme        | port
            HTTP_SCHEME   | HTTP_PORT

            // disabled as support for http certs must be improved
            //HTTPS_SCHEME  | HTTPS_PORT
    }

    static byte[] spec() {
        try (def is = OpenAPIConnectorIT.class.getResourceAsStream('/petstore.json')) {
            assert is != null

            return is.readAllBytes()
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }
}
