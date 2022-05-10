package org.bf2.cos.connector.camel.it

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.apache.qpid.jms.JmsConnectionFactory
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import javax.jms.*
import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static GenericContainer mq

    @Override
    def setupSpec() {
        DockerImageName imageName = DockerImageName.parse('quay.io/artemiscloud/activemq-artemis-broker:0.1.4');
        mq = new GenericContainer(imageName)
        mq.withLogConsumer(logger('tc-activemq'))
        mq.withNetwork(network)
        mq.withNetworkAliases('tc-activemq')
        mq.withExposedPorts(61616)
        mq.withEnv("AMQ_USER", 'artemis')
        mq.withEnv("AMQ_PASSWORD", 'simetraehcapa')
        mq.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(mq)
    }

    def "jms-amqp sink"() {
        setup:
            ConnectionFactory factory = new JmsConnectionFactory("amqp://"+mq.getHost()+":"+mq.getFirstMappedPort());
            Connection connection = factory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection.start();

            javax.jms.Queue queue = session.createQueue("cards");
            MessageConsumer consumer = session.createConsumer(queue);

            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE, """
                - route:
                    from:
                      uri: kamelet:cos-kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                    steps:
                    - to:
                        uri: "kamelet:cos-decoder-json-action"
                    - to:
                        uri: "kamelet:cos-encoder-json-action"
                    - to:
                        uri: kamelet:jms-amqp-10-sink
                        parameters:
                          remoteURI: "amqp://tc-activemq:61616"
                          destinationName: "cards"
                """
            )

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            await(5, TimeUnit.SECONDS) {
                BytesMessage message = consumer.receive() as BytesMessage
                byte[] bytes = message.getBody(byte[])

                def mapper = new ObjectMapper()

                def actual = mapper.readTree(bytes)
                def expected = mapper.readTree(payload)

                return actual == expected
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }
}
