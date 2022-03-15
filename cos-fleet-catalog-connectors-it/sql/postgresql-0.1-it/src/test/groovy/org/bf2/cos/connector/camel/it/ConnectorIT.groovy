package org.bf2.cos.connector.camel.it

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import org.bf2.cos.connector.camel.it.support.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

@Slf4j
class ConnectorIT extends ConnectorSpec {
    PostgreSQLContainer db

    def doSetup() {
        this.db = new PostgreSQLContainer<>('postgres:14.2')
        this.db.withLogConsumer(logger('tc-postgres'))
        this.db.withNetwork(this.network)
        this.db.withNetworkAliases('tc-postgres')
        this.db.start()

        addFileToContainer(
            connector,
            '/etc/camel/application.properties',
            """
            camel.k.sources[0].language = yaml
            camel.k.sources[0].location = file:/etc/camel/sources/route.yaml
            camel.k.sources[0].name = route
            """)
        addFileToContainer(
            connector,
            '/etc/camel/sources/route.yaml',
            """
            - route:
                from:
                  uri: kamelet:kafka-not-secured-source
                  parameters:
                    topic: foo
                    bootstrapServers: "${KafkaContainer.CONTAINER_ALIAS}:${KafkaContainer.KAFKA_OUTSIDE_PORT}"
                steps:
                - to: "log:connector"
                - to:
                    uri: kamelet:postgresql-sink
                    parameters:
                      serverName: "tc-postgres"
                      serverPort: ${PostgreSQLContainer.POSTGRESQL_PORT}
                      username: ${db.username}
                      password: ${db.password}
                      query: INSERT INTO accounts (username,city) VALUES (:#username,:#city)
                      databaseName: ${db.databaseName}
            """)
    }

    def doCleanup() {
        closeQuietly(this.db)
    }

    def "sink"() {
        setup:
            def sql = Sql.newInstance(db.jdbcUrl,  db.username, db.password, db.driverClassName)
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = 'foo'

            sql.execute("""
                CREATE TABLE accounts (
                   username VARCHAR(50) UNIQUE NOT NULL,
                   city VARCHAR(50)
                );
            """)
        when:
            sendToKafka(topic, payload)
        then:
            def records = readFromKafka(topic)
            records.size() == 1
            records.first().value() == payload

            await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .until {
                    sql.rows("""SELECT * FROM accounts WHERE username='oscerd';""").size() == 1
                }

        cleanup:
            sql.close()
    }
}
