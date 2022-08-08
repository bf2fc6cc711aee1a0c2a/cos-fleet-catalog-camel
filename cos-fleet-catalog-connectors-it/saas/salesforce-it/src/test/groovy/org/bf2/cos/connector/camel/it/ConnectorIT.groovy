package org.bf2.cos.connector.camel.it


import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import spock.lang.IgnoreIf
import spock.lang.Stepwise

import java.util.concurrent.TimeUnit

// steps musts be executed in order (create, update, delete)
@Stepwise
@IgnoreIf({
    env['SF_CLIENT_ID'      ] == null ||
    env['SF_CLIENT_SECRET'  ] == null ||
    env['SF_CLIENT_USERNAME'] == null ||
    env['SF_CLIENT_PASSWORD'] == null
})
class ConnectorIT extends KafkaConnectorSpec {
    static def sObjectId

    def "salesforce create sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = """{ "Name" : "${group}" }"""

            def cnt = connectorContainer('salesforce_create_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'salesforce_client_id': System.getenv('SF_CLIENT_ID'),
                'salesforce_client_secret': System.getenv('SF_CLIENT_SECRET'),
                'salesforce_user_name': System.getenv('SF_CLIENT_USERNAME'),
                'salesforce_password': System.getenv('SF_CLIENT_PASSWORD'),
                'salesforce_s_object_name': 'Account'
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            await(30, 1, TimeUnit.SECONDS, () -> {
                def result = ConnectorSupport.query("SELECT name,id from Account WHERE name='${group}'")
                sObjectId = result.records[0].Id

                return result?.totalSize == 1 && sObjectId != null
            })

        cleanup:
            closeQuietly(cnt)
    }

    def "salesforce update sink"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = """{ "Name" : "${group}" }"""

            def cnt = connectorContainer('salesforce_update_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'salesforce_client_id': System.getenv('SF_CLIENT_ID'),
                'salesforce_client_secret': System.getenv('SF_CLIENT_SECRET'),
                'salesforce_user_name': System.getenv('SF_CLIENT_USERNAME'),
                'salesforce_password': System.getenv('SF_CLIENT_PASSWORD'),
                'salesforce_s_object_name': 'Account',
                'salesforce_s_object_id': sObjectId
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            await(30, 1, TimeUnit.SECONDS, () -> {
                def result = ConnectorSupport.query("SELECT name,id from Account WHERE id='${sObjectId}'")
                return result?.totalSize == 1 && result?.records[0].Name == group
            })

        cleanup:
            closeQuietly(cnt)
    }

    def "salesforce delete sink"() {
        setup:
            def topic = topic()
            def payload = """{ "sObjectId" : "${sObjectId}", "sObjectName": "Account" }"""

            def cnt = connectorContainer('salesforce_delete_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'salesforce_client_id': System.getenv('SF_CLIENT_ID'),
                'salesforce_client_secret': System.getenv('SF_CLIENT_SECRET'),
                'salesforce_user_name': System.getenv('SF_CLIENT_USERNAME'),
                'salesforce_password': System.getenv('SF_CLIENT_PASSWORD')
            ])

            cnt.start()
        when:
            kafka.send(topic, payload)
        then:
            await(30, 1, TimeUnit.SECONDS, () -> {
                def result = ConnectorSupport.query("SELECT name,id from Account WHERE id='${sObjectId}'")
                return result?.totalSize == 0
            })

        cleanup:
            closeQuietly(cnt)
    }
}
