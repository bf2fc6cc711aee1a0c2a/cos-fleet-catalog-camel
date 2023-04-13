package org.bf2.cos.connector.camel.it

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.ok
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.verify

@Slf4j
class TelegramConnectorIT extends KafkaConnectorSpec {
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

    def "telegram sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def chatId = ThreadLocalRandom.current().nextInt(1000, 10000);
            def payload = UUID.randomUUID().toString()
            def responsePayload = """
            {
                "ok": true,
                "result": {
                    "message_id": 937,
                    "from": {
                        "id": 770882310,
                        "is_bot": true,
                        "first_name": "camelDemoBot",
                        "username": "camelDemoBot"
                    },
                    "chat": {
                        "id": ${chatId},
                        "first_name": "foo",
                        "last_name": "bar",
                        "type": "private"
                    },
                    "date": 1576249600,
                    "text": "${payload}"
                }
            }
            """

            def path = urlPathEqualTo("/bot${group}/sendMessage")
            def response = ok().withBody(responsePayload)
            def request = post(path)
                    .withRequestBody(WireMock.matchingJsonPath('$.text', equalTo(payload)))
                    .withRequestBody(WireMock.matchingJsonPath('$.chat_id', equalTo("${chatId}")))

            WireMock.configureFor(SCHEME, mock.getHost(), mock.getMappedPort(PORT))
            WireMock.stubFor(request.willReturn(response));

            def cnt = connectorContainer('telegram_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    "telegram_authorization_token": group,
                    "telegram_chat_id": chatId,
            ])

            cnt.withUserProperty("camel.component.telegram.base-uri", "${SCHEME}://${HOST}:${PORT}".toString())
            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            untilAsserted(5, TimeUnit.SECONDS) {
                verify(1, postRequestedFor(path))
            }

            assert WireMock.findUnmatchedRequests().isEmpty()
        cleanup:
            closeQuietly(cnt)
    }
}
