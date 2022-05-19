package org.bf2.cos.connector.camel.it

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.apache.qpid.jms.JmsConnectionFactory
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.MessageConsumer
import javax.jms.Queue
import javax.jms.Session
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
            ConnectionFactory factory = new JmsConnectionFactory("amqp://${mq.host}:${mq.getMappedPort(61616)}")
            Connection connection = factory.createConnection()
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    'jms_amqp_remote_u_r_i': 'amqp://tc-activemq:61616',
                    'jms_amqp_destination_name': 'cards'
            ])

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
