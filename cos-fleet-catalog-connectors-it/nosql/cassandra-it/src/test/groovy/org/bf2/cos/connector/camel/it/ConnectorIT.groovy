package org.bf2.cos.connector.camel.it

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.DataType
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Session
import com.datastax.driver.core.Statement
import com.datastax.driver.core.schemabuilder.SchemaBuilder
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.CassandraContainer

import java.util.concurrent.TimeUnit


@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static CassandraContainer cassandra
    public static final String TEST_KSP = "test_ksp"
    public static final String TEST_TABLE = "test_data"

    @Override
    def setupSpec() {
        cassandra = ContainerImages.CASSANDRA.container(CassandraContainer.class)
        cassandra.withLogConsumer(logger('tc-cassandra'))
        cassandra.withNetwork(network)
        cassandra.withNetworkAliases('tc-cassandra')
        cassandra.start()

        Cluster cluster = cassandra.getCluster();

        try(Session session = cluster.connect()) {
            Statement keyspaceStm = SchemaBuilder.createKeyspace(TEST_KSP)
                    .ifNotExists().with().replication(Map.of("class", "SimpleStrategy", "replication_factor", "1"))

            session.execute(keyspaceStm)
        }

        try(Session session = cluster.connect(TEST_KSP)) {
            Statement tableStm = SchemaBuilder.createTable(TEST_TABLE)
                    .addPartitionKey("id", DataType.timeuuid())
                    .addClusteringColumn("text", DataType.text())

            session.execute(tableStm)
        }
    }

    @Override
    def cleanupSpec() {
        closeQuietly(cassandra)
    }

    def "cassandra sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = '''["ciao"]'''

            def cnt = connectorContainer('cassandra_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': group,
                    'cassandra_connection_host': 'tc-cassandra',
                    'cassandra_connection_port': '9042',
                    'cassandra_keyspace': TEST_KSP,
                    'cassandra_query': 'insert into ' + TEST_TABLE + '(id, text) values (now(), ?)'
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
//            def records = kafka.poll(group, topic)
//            records.size() == 1
//            records.first().value() == payload

            await(10, TimeUnit.SECONDS) {
                Cluster cluster = cassandra.getCluster()

                try(Session session = cluster.connect(TEST_KSP)) {
                    ResultSet rs = session.execute("select count(*) from " + TEST_TABLE + ";")

                    return rs.all().size() == 1
                } catch (Exception e) {
                    return false
                }
            }

        cleanup:
            closeQuietly(cnt)
    }

    def "cassandra source"() {
        setup:
        def topic = topic()
        def payload = 'ciao'

        def cnt = connectorContainer('cassandra_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'cassandra_connection_host': 'tc-cassandra',
                'cassandra_connection_port': '9042',
                'cassandra_keyspace': TEST_KSP,
                'cassandra_query': 'select text from '+ TEST_TABLE
        ])

        cnt.start()
        when:
            Cluster cluster = cassandra.getCluster()

            try(Session session = cluster.connect(TEST_KSP)) {
                session.execute("insert into " + TEST_TABLE + "(id, text) values (now(), '" + payload + "');")
            }
         then:
         await(10, TimeUnit.SECONDS) {
             def record = kafka.poll(cnt.containerId, topic).find {
                 it.value().contains(payload)
             }

             return record != null
         }

        cleanup:
            closeQuietly(cnt)
    }
}
