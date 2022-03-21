package org.bf2.cos.connector.camel.it

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import org.bf2.cos.connector.camel.it.support.KafkaContainer
import org.testcontainers.containers.MSSQLServerContainer

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

@Slf4j
class ConnectorIT extends ConnectorSpec {
    MSSQLServerContainer db

    def doSetup() {
        this.db = new MSSQLServerContainer('mcr.microsoft.com/mssql/server:2017-CU12')
        this.db.acceptLicense()
        this.db.withLogConsumer(logger('tc-sqlserver'))
        this.db.withNetwork(this.network)
        this.db.withNetworkAliases('tc-sqlserver')
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
                    uri: kamelet:sqlserver-sink
                    parameters:
                      serverName: "tc-sqlserver"
                      serverPort: ${MSSQLServerContainer.MS_SQL_SERVER_PORT}
                      username: ${db.username}
                      password: ${db.password}
                      query: INSERT INTO cos.dbo.accounts (username,city) VALUES (:#username,:#city)
                      databaseName: "cos"
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

            sql.execute('CREATE DATABASE cos')
            sql.execute('CREATE TABLE cos.dbo.accounts (username VARCHAR(50) UNIQUE NOT NULL, city VARCHAR(50))')
        when:
            sendToKafka(topic, payload)
        then:
            def records = readFromKafka(topic)
            records.size() == 1
            records.first().value() == payload

            await()
                .atMost(30, TimeUnit.SECONDS)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .until {
                    sql.rows("""SELECT * FROM cos.dbo.accounts WHERE username='oscerd';""").size() == 1
                }

        cleanup:
            sql.close()
    }
}