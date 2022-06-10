package org.bf2.cos.connector.camel.it

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.MySQLContainer

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static MySQLContainer db

    @Override
    def setupSpec() {
        db = new MySQLContainer('mysql:5.7.34')
        db.withLogConsumer(logger('tc-mysql'))
        db.withNetwork(network)
        db.withNetworkAliases('tc-mysql')
        db.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(db)
    }

    def "mysql sink"() {
        setup:
            def sql = Sql.newInstance(db.jdbcUrl,  db.username, db.password, db.driverClassName)
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''

            sql.execute("""
                CREATE TABLE accounts (
                   username VARCHAR(50) UNIQUE NOT NULL,
                   city VARCHAR(50)
                );
            """)

            def topic = topic()
            def group = UUID.randomUUID().toString()

            def cnt = connectorContainer('mysql_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'db_server_name': 'tc-mysql',
                'db_server_port': Integer.toString(MySQLContainer.MYSQL_PORT),
                'db_username': db.username,
                'db_password': db.password,
                'db_query': 'INSERT INTO accounts (username,city) VALUES (:#username,:#city)',
                'db_database_name': db.databaseName
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            await(30, TimeUnit.SECONDS) {
                return sql.rows("""SELECT * FROM accounts WHERE username='oscerd';""").size() == 1
            }

        cleanup:
            closeQuietly(sql)
            closeQuietly(cnt)
    }
}

