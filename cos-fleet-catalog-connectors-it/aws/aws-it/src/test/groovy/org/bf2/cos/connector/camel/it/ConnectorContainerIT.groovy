package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ConnectorContainer
import org.bf2.cos.connector.camel.it.support.SimpleConnectorSpec

@Slf4j
class ConnectorContainerIT extends SimpleConnectorSpec {

    def "container image exposes health and metrics"(String definition) {
        setup:
            def cnt = ConnectorContainer.forDefinition(definition).build()
            cnt.start()
        when:
            def health = cnt.request.get('/q/health')
            def metrics = cnt.request.get("/q/metrics")
        then:
            health.statusCode == 200
            metrics.statusCode == 200

            with (health.as(Map.class)) {
                status == 'UP'
                checks.find {
                    it.name == 'camel-readiness-checks' && it.status == 'UP'
                }
                checks.find {
                    it.name == 'camel-liveness-checks' && it.status == 'UP'
                }
            }
        cleanup:
            closeQuietly(cnt)
        where:
            definition << [
                'aws_cloudwatch_sink_0.1.json',
                'aws_ddb_streams_source_0.1.json',
                'aws_kinesis_source_0.1.json',
                'aws_s3_sink_0.1.json',
                'aws_ses_sink_0.1.json',
                'aws_sqs_sink_0.1.json',
                'aws_ddb_sink_0.1.json' ,
                'aws_kinesis_sink_0.1.json',
                'aws_lambda_sink_0.1.json',
                'aws_s3_source_0.1.json',
                'aws_sns_sink_0.1.json',
                'aws_sqs_source_0.1.json'
            ]
    }
}
