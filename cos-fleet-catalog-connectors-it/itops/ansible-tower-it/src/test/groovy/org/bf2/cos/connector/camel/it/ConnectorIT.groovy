package org.bf2.cos.connector.camel.it

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.verify

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static final int PORT = 8443
    static final String SCHEME = 'https'
    static final String HOST = 'tc-mock'
    static final String JKS_PASS = 'password'
    static final String JKS_PATH = '/etc/wm/cert.jks'
    static final String JKS_CLASSPATH = '/ssl/cert.jks'

    static GenericContainer mock

    @Override
    def setupSpec() {
        mock = ContainerImages.WIREMOCK.container()
        mock.withLogConsumer(logger(HOST))
        mock.withNetwork(KafkaConnectorSpec.network)
        mock.withNetworkAliases(HOST)
        mock.withExposedPorts(PORT)
        mock.waitingFor(Wait.forListeningPort())
        mock.withClasspathResourceMapping(JKS_CLASSPATH, JKS_PATH, BindMode.READ_ONLY, SelinuxContext.SHARED)

        mock.withCommand(
                "--disable-http",
                "--https-port", "${PORT}",
                "--https-keystore", "${JKS_PATH}",
                "--keystore-password", "${JKS_PASS}")

        mock.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(mock)
    }

    @Unroll
    def "ansible_job_template_launch sink (#hostInsecure, #hostVerify)"(boolean hostInsecure, boolean hostVerify) {
        setup:
            def jobId = UUID.randomUUID().toString()
            def username = UUID.randomUUID().toString()
            def password = UUID.randomUUID().toString()

            def path = urlPathEqualTo("/api/v2/job_templates/${jobId}/launch/")
            def request = post(path)
                    .withHeader('Content-Type', equalTo('application/json'))
                    .withBasicAuth(username, password)

            def response = ok()
                    .withBody(''' { "job_template_data": {}, "defaults": {} }''')

            WireMock.configureFor(SCHEME, mock.getHost(), mock.getMappedPort(PORT))
            WireMock.stubFor(request.willReturn(response));

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def cnt = connectorContainer('ansible_tower_job_template_launch_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': KafkaConnectorSpec.kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    'ansible_tower_host': "${HOST}:${PORT}".toString(),
                    'ansible_tower_host_insecure': "${hostInsecure}".toString(),
                    'ansible_tower_host_verify': "${hostVerify}".toString(),
                    'ansible_tower_basic_auth_username': username,
                    'ansible_tower_basic_auth_password': password,
                    'ansible_tower_job_template_id': jobId,
            ])

            cnt.start()
        when:
            KafkaConnectorSpec.kafka.send(topic, payload)
        then:
            def records = KafkaConnectorSpec.kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            untilAsserted(5, TimeUnit.SECONDS) {
                verify(1, postRequestedFor(path))
            }

            assert WireMock.findUnmatchedRequests().isEmpty()
        cleanup:
            closeQuietly(cnt)
        where:
            hostInsecure | hostVerify
            true         | true
            true         | false
    }
}
