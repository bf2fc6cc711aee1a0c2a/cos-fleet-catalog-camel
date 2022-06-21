package org.bf2.cos.connector.camel.it

import com.azure.core.amqp.AmqpTransportType
import com.azure.core.util.IterableStream
import com.azure.messaging.eventhubs.EventHubClientBuilder
import com.azure.messaging.eventhubs.EventHubConsumerClient
import com.azure.messaging.eventhubs.EventProcessorClient
import com.azure.messaging.eventhubs.EventProcessorClientBuilder
import com.azure.messaging.eventhubs.models.EventPosition
import com.azure.messaging.eventhubs.models.PartitionEvent
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import io.restassured.internal.path.json.JSONAssertion
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import spock.lang.IgnoreIf

import java.time.Duration
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.SynchronousQueue
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
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

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
                        'azure_shared_access_key': sharedAccessKey,
                ])
            def logLevel = Optional.ofNullable(System.getenv('AZURE_LOG_LEVEL')).orElse("info")
            cnt.withEnv("AZURE_LOG_LEVEL", logLevel)

            cnt.withEnv("quarkus.log.level", "DEBUG")
            cnt.withEnv("quarkus.log.category.\"org.apache.camel.component\".level", "DEBUG")
            cnt.start()

            // azure sdk client to read the message from EventHub
            def queue = new SynchronousQueue<String>()
            def eventHubClient = new EventProcessorClientBuilder()
                    .initialPartitionEventPosition(new HashMap<String, EventPosition>())
                    .connectionString("Endpoint=sb://${namespaceName}.servicebus.windows.net/;SharedAccessKeyName=${sharedAccessName};SharedAccessKey=${sharedAccessKey};EntityPath=${eventhubName}")
                    .checkpointStore(new SampleCheckpointStore())
                    .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                    .transportType(AmqpTransportType.AMQP)
                    .processEvent({
                        def receivedMessage = it.getEventData().getBodyAsString()
                        queue.add(receivedMessage)
                    })
                    .processError({
                        throw new RuntimeException(it.getThrowable())
                    })
                    .buildEventProcessorClient();
            eventHubClient.start();
            // wait for eventHubClient to init
            sleep(5000)

        when:
            kafka.send(topic, payload)

        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            String message = queue.poll(10, TimeUnit.SECONDS)
            def mapper = new JsonSlurper()
            def expected = mapper.parseText(payload)
            def actual = mapper.parseText(message)
            actual == expected

        cleanup:
            closeQuietly(cnt)
            eventHubClient.stop()
    }
}
