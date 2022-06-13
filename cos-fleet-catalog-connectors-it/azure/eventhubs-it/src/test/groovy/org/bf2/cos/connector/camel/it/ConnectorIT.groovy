package org.bf2.cos.connector.camel.it

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import spock.lang.IgnoreIf

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    @IgnoreIf({
        env['AZURE_EVENTHUB_NAME'      ] == null ||
        env['AZURE_NAMESPACE_NAME'     ] == null ||
        env['AZURE_SHARED_ACCESS_NAME' ] == null ||
        env['AZURE_SHARED_ACCESS_KEY'  ] == null
    })
    def "azure-eventhubs sink"() {
        setup:
            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def cnt = connectorContainer('azure_eventhubs_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    'azure_eventhub_name': System.getenv('AZURE_EVENTHUB_NAME'),
                    'azure_namespace_name': System.getenv('AZURE_NAMESPACE_NAME'),
                    'azure_shared_access_name': System.getenv('AZURE_SHARED_ACCESS_NAME'),
                    'azure_shared_access_key': System.getenv('AZURE_SHARED_ACCESS_KEY'),
            ])

            cnt.start()

        when:
            kafka.send(topic, payload)
        then:
            def slurper = new JsonSlurper()

            await(60, TimeUnit.SECONDS) {
                kafka.poll(group, topic).forEach(r -> {
                    def result = slurper.parse(r.value().getBytes(StandardCharsets.UTF_8))
                })

                return false
            }

        cleanup:
            closeQuietly(cnt)
    }
}
