package org.bf2.cos.connector.camel.it

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static final int PORT = 8080
    static final String SCHEME = 'http'
    static final String HOST = 'tc-mock'

    static GenericContainer mock

    @Override
    def setupSpec() {
        mock = ContainerImages.WIREMOCK.container()
        mock.withLogConsumer(logger(HOST))
        mock.withNetwork(network)
        mock.withNetworkAliases(HOST)
        mock.withExposedPorts(PORT)
        mock.waitingFor(Wait.forListeningPort())
        mock.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(mock)
    }

    def "telegram source"() {
        given:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = """
            {
              "ok": true,
              "result": [
                {
                  "update_id": 525704898,
                  "message": {
                    "message_id": 179,
                    "from": {
                      "id": 1585844777,
                      "first_name": "John",
                      "last_name": "Doe"
                    },
                    "chat": {
                      "id": -45658,
                      "title": "A chat group",
                      "type": "group"
                    },
                    "date": 1463436626,
                    "text": "${group}"
                  }
                }
              ]
            }
            """.stripIndent()

            def path = urlPathEqualTo("/bot${group}/getUpdates")
            def res = ok().withBody(payload)
            def req = get(path).withQueryParam('limit', equalTo('100')).withQueryParam('timeout', equalTo('30'))

            WireMock.configureFor(SCHEME, mock.getHost(), mock.getMappedPort(PORT))
            WireMock.stubFor(req.willReturn(res))

            def cnt = connectorContainer('telegram_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                "telegram_authorization_token": group,
            ])

            cnt.withUserProperty("camel.component.telegram.base-uri", "${SCHEME}://${HOST}:${PORT}".toString())
            cnt.withUserProperty('quarkus.log.category."org.apache.camel".level', 'INFO')
        when:
            cnt.start()
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            def record = records.first()
            def actual = TestUtils.SLURPER.parseText(record.value())

            record.headers().lastHeader('chat-id').value() == '-45658'.bytes
            actual.text == group

        cleanup:
            closeQuietly(cnt)
    }
}
