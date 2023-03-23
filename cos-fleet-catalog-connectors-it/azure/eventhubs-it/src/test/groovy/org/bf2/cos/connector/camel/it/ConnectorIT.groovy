package org.bf2.cos.connector.camel.it

import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.eventhubs.EventData
import com.azure.messaging.eventhubs.EventDataBatch
import com.azure.messaging.eventhubs.EventHubClientBuilder
import com.azure.messaging.eventhubs.EventProcessorClientBuilder
import com.azure.messaging.eventhubs.models.EventPosition
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import spock.lang.IgnoreIf

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

    def "azure-eventhubs sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = """{ "value": "4", "suit": "${group}" }"""
            def headers = Map.of('foo', 'bar', 'foo2', group)

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
            sleep(10000)

        when:
            kafka.send(topic, payload, headers)

        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            def expected = TestUtils.SLURPER.parseText(payload)

            EventData message = queue.poll(10, TimeUnit.SECONDS)
            message != null

            def actual = TestUtils.SLURPER.parseText(message.getBodyAsString())

            actual == expected

            // match all custom headers
            headers.every({
                it.getValue() == message.getProperties().get(it.getKey())
            })
        cleanup:
            closeQuietly(cnt)
            eventHubClient.stop()
    }

    def "azure-eventhubs source"() {
        setup:
            def start = Instant.now();
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = """{ "value": "4", "suit": "${group}" }"""

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
            cnt.withCamelComponentDebugEnv()

            cnt.start()

            // azure sdk client to write the message to EventHub
            def producer = new EventHubClientBuilder()
                    .connectionString("Endpoint=sb://${namespaceName}.servicebus.windows.net/;SharedAccessKeyName=${sharedAccessName};SharedAccessKey=${sharedAccessKey};EntityPath=${eventhubName}")
                    .buildProducerClient();

            // wait for eventHubClient to init
            sleep(10000)

            EventData event = new EventData(payload);
        when:
            EventDataBatch eventDataBatch = producer.createBatch();
            if (!eventDataBatch.tryAdd(event)) {
                throw new RuntimeException("Couldn't add event to batch.")
            }
            producer.send(eventDataBatch);
            log.info("Message written to eventhub: {}", payload)
        then:
            await(30, TimeUnit.SECONDS) {
                def records = kafka.poll(group, topic)
                return records.any({
                    log.info("Checking if record with value ({}) is equal to payload ({})", it.value(), payload)
                    it.value() == payload
                })
            }
        cleanup:
            closeQuietly(cnt)
            closeQuietly(producer)
    }

}
