package org.bf2.cos.connector.camel.it

import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.eventhubs.EventData
import com.azure.messaging.eventhubs.EventHubClientBuilder
import com.azure.messaging.eventhubs.EventProcessorClientBuilder
import com.azure.messaging.eventhubs.models.EventPosition
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import spock.lang.IgnoreIf

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@IgnoreIf({
    !hasEnv('AZURE_EVENTHUB_NAME'      ) ||
    !hasEnv('AZURE_NAMESPACE_NAME'     ) ||
    !hasEnv('AZURE_SHARED_ACCESS_NAME' ) ||
    !hasEnv('AZURE_SHARED_ACCESS_KEY'  )
})
@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    def "azure-eventhubs sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''
            def headers = Map.of('foo', 'bar', 'foo2', '123')

            def namespaceName = System.getenv('AZURE_NAMESPACE_NAME')
            def sharedAccessName = System.getenv('AZURE_SHARED_ACCESS_NAME')
            def sharedAccessKey = System.getenv('AZURE_SHARED_ACCESS_KEY')
            def eventhubName = System.getenv('AZURE_EVENTHUB_NAME')

            def cnt = connectorContainer('azure_eventhubs_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'azure_eventhub_name': eventhubName,
                'azure_namespace_name': namespaceName,
                'azure_shared_access_name': sharedAccessName,
                'azure_shared_access_key': sharedAccessKey
            ])

            cnt.withEnv("AZURE_LOG_LEVEL", System.getenv().getOrDefault('AZURE_LOG_LEVEL', 'info'))
            cnt.withUserProperty('quarkus.log.level', 'DEBUG')
            cnt.withUserProperty('quarkus.log.category."org.apache.camel.component".level', 'DEBUG')
            cnt.start()

            def queue = new LinkedBlockingQueue<EventData>()

            // azure sdk client to read the message from EventHub
            def eventHubClient = new EventProcessorClientBuilder()
                .initialPartitionEventPosition(new HashMap<String, EventPosition>())
                .connectionString("Endpoint=sb://${namespaceName}.servicebus.windows.net/;SharedAccessKeyName=${sharedAccessName};SharedAccessKey=${sharedAccessKey};EntityPath=${eventhubName}")
                .checkpointStore(new SampleCheckpointStore())
                .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                .transportType(AmqpTransportType.AMQP)
                .processEvent({ queue.add(it.getEventData()) })
                .processError({ throw new RuntimeException(it.getThrowable()) })
                .buildEventProcessorClient()

            eventHubClient.start();

            // wait for eventHubClient to init
            sleep(5000)

        when:
            kafka.send(topic, payload, headers)

        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            def mapper = new JsonSlurper()
            def expected = mapper.parseText(payload)

            untilAsserted(10, TimeUnit.SECONDS) {
                EventData message = queue.poll(1, TimeUnit.SECONDS)

                message != null

                def actual = mapper.parseText(message.getBodyAsString())

                actual == expected

                // match all custom headers
                headers.every({
                    it.getValue() == message.getProperties().get(it.getKey())
                })
            }
        cleanup:
            closeQuietly(cnt)
            eventHubClient.stop()
    }
}
