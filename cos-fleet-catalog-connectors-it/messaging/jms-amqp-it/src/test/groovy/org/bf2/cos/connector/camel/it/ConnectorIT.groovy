package org.bf2.cos.connector.camel.it

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.apache.kafka.common.header.Header
import org.apache.qpid.jms.JmsConnectionFactory
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.AwaitStrategy
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.ContainerState
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.WaitStrategy

import java.nio.charset.StandardCharsets;
import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.JMSException
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {

    static ObjectMapper mapper;
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

        mapper = new ObjectMapper()
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

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic : ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
            """)

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

                if (bytes == null) {
                    return false
                }

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

    def "jms-amqp sink with value from header"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''
            def expectedPayload = """{ "value": "4", "suit": "${topic}" }"""
            def expectedHeaderName = 'foo'
            def expectedHeaderVal = 'bar'

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - setHeader:
                    name: ${expectedHeaderName}
                    constant: ${expectedHeaderVal}
                - transform:
                    jq: '.suit = header("kafka.TOPIC")'
            """)

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

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                def expected = mapper.readTree(expectedPayload)

                return actual == expected
                        && message.getStringProperty(expectedHeaderName) == expectedHeaderVal
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with processors"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: '.suit'
            """)

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

                if (bytes == null) {
                    return false
                }

                return 'hearts' == new String(bytes, StandardCharsets.UTF_8)
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with filter"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload1 = '''{ "foo": "bar" }'''
            def payload2 = '''{ "foo": "baz" }'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - filter:
                    jq: '.foo == "baz"'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload1)
            kafka.send(topic, payload2)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 2

            await(5, TimeUnit.SECONDS) {
                BytesMessage message = consumer.receive() as BytesMessage
                byte[] bytes = message.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                def expected = mapper.readTree(payload2)

                return actual == expected
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp source with ValueToKey"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageProducer producer = session.createProducer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_source_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - setHeader:
                    name: 'kafka.KEY'
                    jq: '{ field1, field3 }'
            """)

            cnt.start()
        when:
            def msg = session.createTextMessage(payload)
            producer.send(msg)
        then:
            await(5, TimeUnit.SECONDS) {
                def record = kafka.poll(cnt.containerId, topic).first()
                def key = mapper.readTree(record.key())
                def expectedKey = mapper.readTree('''{"field1": 1, "field3": "three"}''')
                def value = mapper.readTree(record.value())
                def expectedValue = mapper.readTree(payload)
                return key == expectedKey && value == expectedValue
            }

        cleanup:
        closeQuietly(producer)
        closeQuietly(session)
        closeQuietly(connection)
        closeQuietly(cnt)
    }

    def "jms-amqp sink with MaskField"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "foo": "bar", "passwd": "my password", "secret": "my secret" }'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: '.passwd = "masked" | .secret = "masked"'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

            def actual = mapper.readTree(bytes)
            return actual.get("passwd").asText() == "masked" && actual.get("secret").asText() == "masked";
        }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp source with InsertHeader"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageProducer producer = session.createProducer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_source_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - setHeader:
                    name: 'MyKafkaHeader'
                    jq: '.field2'
            """)

            cnt.start()
        when:
            def msg = session.createTextMessage(payload)
            producer.send(msg)
        then:
            await(5, TimeUnit.SECONDS) {
                def record = kafka.poll(cnt.containerId, topic).first()
                def myKafkaHeader = record.headers().headers("MyKafkaHeader").first();
                def myKafkaHeaderStr = new String(myKafkaHeader.value())
                def value = mapper.readTree(record.value())
                def expectedValue = mapper.readTree(payload)
                return myKafkaHeaderStr == "two" && value == expectedValue
            }

        cleanup:
            closeQuietly(producer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with InsertField"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{ "foo": "bar" }'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                    kafka_topic: ${topic}
                    kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                    kafka_consumer_group: ${UUID.randomUUID()}
                    jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                    jms_amqp_destination_name: 'cards'
                    processors:
                    - transform:
                        jq: '.addition = "added field"'
                """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)

                return actual.get("addition").asText() == "added field"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with HoistField"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: '{ "wrapped": .}'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                def expected = mapper.readTree(payload)
                return actual.get("wrapped") == expected
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with ExtractField"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: '.field3'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                return new String(bytes) == "three"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with ReplaceField"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    # There's a bit easier way, just delete the field and add the new field. However in that way the field order changes.
                    jq: 'to_entries | map(if .key == "field3" then .key = "field3-modified" else . end) | from_entries'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                Number i = actual.fields().findIndexValues { (it.getKey() == "field3-modified") }.first()
                return i == 2 && actual.get("field3") == null && actual.get("field3-modified").asText() == "three"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with ReplaceField with cos-rename-field-action Kamelet"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''{"field1": 1, "field2": "two", "field3": "three", "field4": "four"}'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - to:
                    uri: 'kamelet:cos-rename-field-action?from=field3&to=field3-modified'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                Number i = actual.fields().findIndexValues { (it.getKey() == "field3-modified") }.first()
                return i == 2 && actual.get("field3") == null && actual.get("field3-modified").asText() == "three"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp source with Content based routing"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageProducer producer = session.createProducer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payloadNA = '''{"destination": "NA", "command": "CMD.NA.01"}'''
            kafka.createTopic("NA")
            def payloadEMEA = '''{"destination": "EMEA", "command": "CMD.EMEA.01"}'''
            kafka.createTopic("EMEA")
            def payloadLATAM = '''{"destination": "LATAM", "command": "CMD.LATAM.01"}'''
            kafka.createTopic("LATAM")
            def payloadAPAC = '''{"destination": "APAC", "command": "CMD.APAC.01"}'''
            kafka.createTopic("APAC")

            def cnt = connectorContainer('jms_amqp_10_source_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - setHeader:
                    name: 'kafka.OVERRIDE_TOPIC'
                    jq:
                        expression: '.destination'
                        resultType: 'java.lang.String' # workaround for https://issues.apache.org/jira/browse/CAMEL-18386
            """)

            cnt.start()
        when:
            def msg = session.createTextMessage(payloadNA)
            producer.send(msg)
            msg = session.createTextMessage(payloadEMEA)
            producer.send(msg)
            msg = session.createTextMessage(payloadLATAM)
            producer.send(msg)
            msg = session.createTextMessage(payloadAPAC)
            producer.send(msg)
        then:
            await(20, TimeUnit.SECONDS) {
                def record = kafka.poll(group, "NA").first()
                def valueNA = mapper.readTree(record.value())
                record = kafka.poll(group, "EMEA").first()
                def valueEMEA = mapper.readTree(record.value())
                record = kafka.poll(group, "LATAM").first()
                def valueLATAM = mapper.readTree(record.value())
                record = kafka.poll(group, "APAC").first()
                def valueAPAC = mapper.readTree(record.value())

                return valueNA.get("command").asText() == "CMD.NA.01"
                && valueEMEA.get("command").asText() == "CMD.EMEA.01"
                && valueLATAM.get("command").asText() == "CMD.LATAM.01"
                && valueAPAC.get("command").asText() == "CMD.APAC.01"
            }

        cleanup:
            closeQuietly(producer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp source with Content based routing: choice-when"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageProducer producer = session.createProducer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payloadNA = '''{"destination": "NA", "command": "CMD.NA.01"}'''
            kafka.createTopic("TOPIC.NA")
            def payloadEMEA = '''{"destination": "EMEA", "command": "CMD.EMEA.01"}'''
            kafka.createTopic("TOPIC.EMEA")
            def payloadLATAM = '''{"destination": "LATAM", "command": "CMD.LATAM.01"}'''
            kafka.createTopic("TOPIC.LATAM")
            def payloadAPAC = '''{"destination": "APAC", "command": "CMD.APAC.01"}'''
            kafka.createTopic("TOPIC.APAC")

            def cnt = connectorContainer('jms_amqp_10_source_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - choice:
                    when:
                    - jq: '.destination == "NA"'
                      steps:
                        - setHeader:
                            name: 'kafka.OVERRIDE_TOPIC'
                            constant: "TOPIC.NA"
                    - jq: '.destination == "EMEA"'
                      steps:
                      - setHeader:
                          name: 'kafka.OVERRIDE_TOPIC'
                          constant: "TOPIC.EMEA"
                    - jq: '.destination == "LATAM"'
                      steps:
                        - setHeader:
                            name: 'kafka.OVERRIDE_TOPIC'
                            constant: "TOPIC.LATAM"
                    - jq: '.destination == "APAC"'
                      steps:
                      - setHeader:
                          name: 'kafka.OVERRIDE_TOPIC'
                          constant: "TOPIC.APAC"
            """)

            cnt.start()
        when:
            def msg = session.createTextMessage(payloadNA)
            producer.send(msg)
            msg = session.createTextMessage(payloadLATAM)
            producer.send(msg)
            msg = session.createTextMessage(payloadEMEA)
            producer.send(msg)
            msg = session.createTextMessage(payloadAPAC)
            producer.send(msg)
        then:
            await(20, TimeUnit.SECONDS) {
                def record = kafka.poll(group, "TOPIC.NA").first()
                def valueNA = mapper.readTree(record.value())
                record = kafka.poll(group, "TOPIC.EMEA").first()
                def valueEMEA = mapper.readTree(record.value())
                record = kafka.poll(group, "TOPIC.LATAM").first()
                def valueLATAM = mapper.readTree(record.value());
                record = kafka.poll(group, "TOPIC.APAC").first()
                def valueAPAC = mapper.readTree(record.value())

                return valueNA.get("command").asText() == "CMD.NA.01"
                        && valueEMEA.get("command").asText() == "CMD.EMEA.01"
                        && valueLATAM.get("command").asText() == "CMD.LATAM.01"
                        && valueAPAC.get("command").asText() == "CMD.APAC.01"
            }

        cleanup:
            closeQuietly(producer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    /**
     * Collection (i.e. array in JSON)
     */

    def "jms-amqp sink with Filter objects in an array based on the contents of a key"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)
            while (consumer.receiveNoWait() != null) {}

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''[
                { "id": "A001", "value": "value-A001" },
                { "id": "B002", "value": "value-B002" },
                { "id": "A003", "value": "value-A003" },
                { "id": "B004A", "value": "value-B004A" }
            ]'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: 'map( select(.id | test("^A"; "i")))'
                - log: ">>>>>>>>>> \${body}"
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(10, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                return actual.isArray() && actual.size() == 2
                        && actual.get(0).get("value").asText() == "value-A001"
                        && actual.get(1).get("value").asText() == "value-A003"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with Concatenate fields for each array item"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''[
                { "first": "John", "last": "Leacu" },
                { "first": "Paul", "last": "Ross" },
                { "first": "Ted", "last": "Johnson" },
                { "first": "Ken", "last": "Graham" }
            ]'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: 'map({name: (.first + " " + .last) })'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(10, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                println(actual)
                return actual.isArray() && actual.size() == 4
                        && actual.get(0).get("name").asText() == "John Leacu"
                        && actual.get(1).get("name").asText() == "Paul Ross"
                        && actual.get(2).get("name").asText() == "Ted Johnson"
                        && actual.get(3).get("name").asText() == "Ken Graham"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }

    def "jms-amqp sink with Collect values from a nested array"() {
        setup:
            Connection connection = createConnection(mq)
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            connection.start()

            Queue queue = session.createQueue("cards")
            MessageConsumer consumer = session.createConsumer(queue)

            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''[
            {
                "first": "Ted",
                "last": "Johnson",
                "emails": [
                    "tj@bf2.org",
                    "tedjohnson@bf2.org"
                ]
            },
            {
                "first": "Ken",
                "last": "Graham",
                "emails": [
                    "kg@bf2.org",
                    "kengraham@bf2.org"
                ]
            }
            ]'''

            def cnt = connectorContainer('jms_amqp_10_sink_0.1.json', """
                kafka_topic: ${topic}
                kafka_bootstrap_servers: ${kafka.outsideBootstrapServers}
                kafka_consumer_group: ${UUID.randomUUID()}
                jms_amqp_remote_u_r_i: 'amqp://tc-activemq:61616'
                jms_amqp_destination_name: 'cards'
                processors:
                - transform:
                    jq: '[ .[].emails[] ]'
            """)

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1

            await(5, TimeUnit.SECONDS) {
                BytesMessage msg = consumer.receive() as BytesMessage
                byte[] bytes = msg.getBody(byte[])

                if (bytes == null) {
                    return false
                }

                def actual = mapper.readTree(bytes)
                println(actual)
                return actual.isArray() && actual.size() == 4
                        && actual.get(0).asText() == "tj@bf2.org"
                        && actual.get(1).asText() == "tedjohnson@bf2.org"
                        && actual.get(2).asText() == "kg@bf2.org"
                        && actual.get(3).asText() == "kengraham@bf2.org"
            }

        cleanup:
            closeQuietly(consumer)
            closeQuietly(session)
            closeQuietly(connection)
            closeQuietly(cnt)
    }
}
