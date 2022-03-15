package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import org.testcontainers.containers.localstack.LocalStackContainer

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

@Slf4j
class ConnectorSourceIT extends ConnectorSpec {
    static String TOPIC = 'foo'

    AWSContainer aws

    def doSetup() {
        this.aws = new AWSContainer(network, LocalStackContainer.Service.SQS)
        this.aws.start()

        addFileToConnectorContainer(
            '/etc/camel/application.properties',
            """
            camel.k.sources[0].language = yaml
            camel.k.sources[0].location = file:/etc/camel/sources/route.yaml
            camel.k.sources[0].name = route
            """)
        addFileToConnectorContainer(
            '/etc/camel/sources/route.yaml',
            """
            - route:
                from:
                  uri: kamelet:aws-sqs-source
                  parameters:
                    accessKey: ${aws.accessKey}
                    secretKey: ${aws.secretKey}
                    region: ${aws.region}
                    queueNameOrArn: $TOPIC
                    amazonAWSHost: ${AWSContainer.CONTAINER_ALIAS}
                    autoCreateQueue: true
                    uriEndpointOverride: ${aws.endpoint}
                    overrideEndpoint: true
                steps:
                - to: 
                    uri: "kamelet:cos-log-action"
                    parameters:
                      multiLine: true
                      showProperties: false
                      showHeaders: true
                      showBody: true
                      showBodyType: true
                - to:
                    uri: kamelet:kafka-not-secured-sink
                    parameters:
                      topic: $TOPIC
                      bootstrapServers: ${kafka.outsideBootstrapServers}
            """)
    }

    def doCleanup() {
        closeQuietly(this.aws)
    }

    def "source"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''


            def sqs = aws.sqs()
            def queue = sqs.createQueue(TOPIC)
            def queueUrl = queue.queueUrl.replace(AWSContainer.CONTAINER_ALIAS, 'localhost')
        when:
            sqs.sendMessage(queueUrl, payload)
        then:
            await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .until {
                    def record = readFromKafka(TOPIC).find {
                        it.value() == payload
                    }

                    return record != null
                }
    }
}

