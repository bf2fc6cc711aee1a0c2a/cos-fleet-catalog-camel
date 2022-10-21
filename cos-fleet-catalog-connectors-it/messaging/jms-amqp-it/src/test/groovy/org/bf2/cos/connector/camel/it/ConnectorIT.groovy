package org.bf2.cos.connector.camel.it

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.apache.qpid.jms.JmsConnectionFactory
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.AwaitStrategy
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.ContainerState
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.WaitStrategy

import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.JMSException
import javax.jms.MessageConsumer
import javax.jms.Queue
import javax.jms.Session
import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static GenericContainer mq

    @Override
    def setupSpec() {
        mq = ContainerImages.ACTIVEMQ_ARTEMIS.container()
        mq.withLogConsumer(logger('tc-activemq'))
        mq.withNetwork(network)
        mq.withNetworkAliases('tc-activemq')
        mq.withExposedPorts(61616)
        mq.withEnv('AMQ_USER', 'artemis')
        mq.withEnv('AMQ_PASSWORD', 'simetraehcapa')
        mq.withEnv('AMQ_EXTRA_ARGS', '--no-autotune --mapped --no-fsync')
        mq.waitingFor(waitForConnection())
        mq.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(mq)
    }

    def "jms-amqp sink"() {
        setup:
            Connection connection = createConnection(mq)
            connection.start()

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
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


    static Connection createConnection(ContainerState state) {
        def remoteURI = "amqp://${state.host}:${state.getMappedPort(61616)}"
        def factory = new JmsConnectionFactory(remoteURI)

        return factory.createConnection()
    }

    static WaitStrategy waitForConnection() {
        return new AwaitStrategy() {
            @Override
            boolean ready() {
                try (Connection connection = createConnection(target)) {
                    connection.start()
                } catch (JMSException e) {
                    return false
                }

                return true
            }
        }
    }
}
