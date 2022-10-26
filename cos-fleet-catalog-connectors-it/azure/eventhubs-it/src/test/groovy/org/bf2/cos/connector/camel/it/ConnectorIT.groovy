package org.bf2.cos.connector.camel.it

import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.eventhubs.EventData
import com.azure.messaging.eventhubs.EventHubClientBuilder
import com.azure.messaging.eventhubs.EventProcessorClientBuilder
import com.azure.messaging.eventhubs.models.EventPosition
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import spock.lang.Ignore
import spock.lang.IgnoreIf

import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@IgnoreIf({
    !hasEnv('AZURE_EVENTHUB_NAME'      ) ||
    !hasEnv('AZURE_NAMESPACE_NAME'     ) ||
    !hasEnv('AZURE_SHARED_ACCESS_NAME' ) ||
    !hasEnv('AZURE_SHARED_ACCESS_KEY'  ) ||
    !hasEnv('AZURE_BLOB_ACCOUNT_NAME'  ) ||
    !hasEnv('AZURE_BLOB_ACCESS_KEY'    ) ||
    !hasEnv('AZURE_BLOB_CONTAINER_NAME')
})
@Slf4j
class ConnectorIT extends KafkaConnectorSpec {

    @Ignore("Fails on CI")
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

            cnt.withEnv("AZURE_LOG_LEVEL", System.getenv().getOrDefault('AZURE_LOG_LEVEL', 'warn'))
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

            def expected = TestUtils.SLURPER.parseText(payload)

            untilAsserted(10, TimeUnit.SECONDS) {
                EventData message = queue.poll(1, TimeUnit.SECONDS)

                message != null

                def actual = TestUtils.SLURPER.parseText(message.getBodyAsString())

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

    @Ignore("Fails on CI")
    def "azure-eventhubs source"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def namespaceName = System.getenv('AZURE_NAMESPACE_NAME')
            def sharedAccessName = System.getenv('AZURE_SHARED_ACCESS_NAME')
            def sharedAccessKey = System.getenv('AZURE_SHARED_ACCESS_KEY')
            def eventhubName = System.getenv('AZURE_EVENTHUB_NAME')
            def blobAccountName = System.getenv('AZURE_BLOB_ACCOUNT_NAME')
            def blobAccessKey = System.getenv('AZURE_BLOB_ACCESS_KEY')
            def blobContainerName = System.getenv('AZURE_BLOB_CONTAINER_NAME')

            def cnt = connectorContainer('azure_eventhubs_source_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'azure_eventhub_name': eventhubName,
                    'azure_namespace_name': namespaceName,
                    'azure_shared_access_name': sharedAccessName,
                    'azure_shared_access_key': sharedAccessKey,
                    'azure_blob_account_name': blobAccountName,
                    'azure_blob_access_key': blobAccessKey,
                    'azure_blob_container_name': blobContainerName,
            ])

            cnt.withEnv("AZURE_LOG_LEVEL", System.getenv().getOrDefault('AZURE_LOG_LEVEL', 'warn'))
            cnt.withUserProperty('quarkus.log.level', 'DEBUG')
            cnt.withUserProperty('quarkus.log.category."org.apache.camel.component".level', 'DEBUG')
            cnt.start()

            // azure sdk client to write the message to EventHub
            def eventHubClient = new EventHubClientBuilder()
                    .connectionString("Endpoint=sb://${namespaceName}.servicebus.windows.net/;SharedAccessKeyName=${sharedAccessName};SharedAccessKey=${sharedAccessKey};EntityPath=${eventhubName}")
                    .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                    .transportType(AmqpTransportType.AMQP)
                    .buildProducerClient();

            def eventData = new EventData(payload)

            def batch = eventHubClient.createBatch();
            batch.tryAdd(eventData)
        when:
            eventHubClient.send(batch)
            sleep(1000)
        then:
            def records = kafka.poll(group, topic)
            records.size() > 0

            def lastMessage = records.last()
            lastMessage.value() == payload

            def messageTimestamp = lastMessage.timestamp()
            Duration.between(Instant.ofEpochMilli(messageTimestamp), Instant.now()).toSeconds() < 5

        cleanup:
            closeQuietly(cnt)
            closeQuietly(eventHubClient)
    }

}
