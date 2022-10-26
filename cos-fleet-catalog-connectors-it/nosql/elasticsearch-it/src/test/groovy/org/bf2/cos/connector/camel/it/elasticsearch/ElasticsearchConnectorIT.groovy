package org.bf2.cos.connector.camel.it.elasticsearch

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import org.elasticsearch.client.Request

import java.util.concurrent.TimeUnit

import static org.bf2.cos.connector.camel.it.elasticsearch.ElasticsearchSupport.client
import static org.bf2.cos.connector.camel.it.elasticsearch.ElasticsearchSupport.elasticsearchContainer

@Slf4j
class ElasticsearchConnectorIT extends KafkaConnectorSpec {
    private static final String ELASTIC_SECURED_USER = 'elastic'
    private static final String ELASTIC_SECURED_PASSWORD = 'supersecret'
    private static final String ELASTIC_SECURED_ALIAS = 'tc-elastic-secured'
    private static final String ELASTIC_ALIAS = 'tc-elastic'

    def "elasticsearch sink"(String alias, String user, String password) {
        setup:
            def elastic = elasticsearchContainer(network, alias, user, password)
            elastic.start()

            def topic = topic()
            def payload = """{ "kafka_topic": "${topic}" }"""
            def client = client(elastic, user, password)

            def get = new Request("GET", "/${topic}")
            def delete = new Request("DELETE", "/${topic}")
            def search = new Request("GET", "/${topic}/_search")

            def props = [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'elasticsearch_host_addresses': alias,
                'elasticsearch_cluster_name': topic,
                'elasticsearch_enable_s_s_l': 'false'
            ]

            if (user != null && password != null) {
                props['elasticsearch_user'] = user
                props['elasticsearch_password'] = password
            }

            def cnt = connectorContainer('elasticsearch_sink_0.1.json', props)

            cnt.withUserProperty('quarkus.log.category."org.apache.camel.component.elasticsearch".level', 'DEBUG')
            cnt.start()

        when:
            kafka.send(topic, payload, [
                'indexId': topic,
                'indexName': topic
            ])

        then:
            def records = kafka.poll(topic)
            records.size() == 1
            records.first().value() == payload

            until(10, TimeUnit.SECONDS) {
                try {
                    def r = client.performRequest(get)
                    if (r.statusLine.statusCode != 200) {
                        return false
                    }

                    def s = client.performRequest(search)
                    if (r.statusLine.statusCode != 200) {
                        return false
                    }

                    try (InputStream is = s.entity.content) {
                        def doc = TestUtils.SLURPER.parse(is)
                        if (doc.hits.total.value != 1) {
                            return false
                        }

                        return doc.hits.hits[0]._source.kafka_topic == topic
                    }
                } catch(Exception e) {
                    log.info('Failed to invoke elasticsearch endpoint, will retry (reason: {})', e.message)
                    return false
                }
            }

        cleanup:
            if (client != null) {
                client.performRequest(delete)
            }

            closeQuietly(cnt)
            closeQuietly(elastic)

        where:
            alias                 | user                 | password
            ELASTIC_ALIAS         | null                 | null
            ELASTIC_SECURED_ALIAS | ELASTIC_SECURED_USER | ELASTIC_SECURED_PASSWORD
    }
}
