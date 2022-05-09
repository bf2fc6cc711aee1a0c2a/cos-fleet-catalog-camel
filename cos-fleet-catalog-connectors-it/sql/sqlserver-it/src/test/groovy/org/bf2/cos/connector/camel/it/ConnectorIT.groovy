package org.bf2.cos.connector.camel.it

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.MSSQLServerContainer

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static MSSQLServerContainer db

    @Override
    def setupSpec() {
        db = new MSSQLServerContainer('mcr.microsoft.com/mssql/server:2017-CU12')
        db.acceptLicense()
        db.withLogConsumer(logger('tc-sqlserver'))
        db.withNetwork(network)
        db.withNetworkAliases('tc-sqlserver')
        db.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(db)
    }

    def "sqlserver sink"() {
        setup:
            def sql = Sql.newInstance(db.jdbcUrl,  db.username, db.password, db.driverClassName)
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''

            sql.execute('CREATE DATABASE cos')
            sql.execute('CREATE TABLE cos.dbo.accounts (username VARCHAR(50) UNIQUE NOT NULL, city VARCHAR(50))')

            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()

            def cnt = connectorContainer(
                ConnectorSupport.CONTAINER_IMAGE,
                """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - to:
                        uri: kamelet:sqlserver-sink
                        parameters:
                          serverName: "tc-sqlserver"
                          serverPort: ${MSSQLServerContainer.MS_SQL_SERVER_PORT}
                          username: ${db.username}
                          password: ${db.password}
                          query: INSERT INTO cos.dbo.accounts (username,city) VALUES (:#username,:#city)
                          databaseName: "cos"
                """
            )

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            def records = kafka.poll(group, topic)
            records.size() == 1
            records.first().value() == payload

            await(30, TimeUnit.SECONDS) {
                return sql.rows("""SELECT * FROM cos.dbo.accounts WHERE username='oscerd';""").size() == 1
            }

        cleanup:
            closeQuietly(sql)
            closeQuietly(cnt)
    }
}
