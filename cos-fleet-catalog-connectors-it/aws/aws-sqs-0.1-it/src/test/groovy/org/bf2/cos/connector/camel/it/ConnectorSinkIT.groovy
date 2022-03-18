package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorSinkIT extends ConnectorSpec {
    static String TOPIC = 'foo'

    AWSContainer aws

    def doSetup() {
        this.aws = new AWSContainer(network, 'sqs')
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
                  uri: kamelet:kafka-not-secured-source
                  parameters:
                    topic: $TOPIC
                    bootstrapServers: ${kafka.outsideBootstrapServers}
                steps:
                - removeHeader:
                    name: "kafka.HEADERS"
                - to: 
                    uri: "kamelet:cos-log-action"
                    parameters:
                      multiLine: true
                      showProperties: false
                      showHeaders: true
                      showBody: true
                      showBodyType: true
                - to:
                    uri: kamelet:aws-sqs-sink
                    parameters:
                      accessKey: ${aws.credentials.accessKeyId()}
                      secretKey: ${aws.credentials.secretAccessKey()}
                      region: ${aws.region}
                      queueNameOrArn: $TOPIC
                      amazonAWSHost: ${AWSContainer.CONTAINER_ALIAS}
                      autoCreateQueue: true
                      uriEndpointOverride: ${aws.endpoint}
                      overrideEndpoint: true
            """)
    }

    def doCleanup() {
        closeQuietly(this.aws)
    }

    def "sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def sqs = aws.sqs()
            def request  = CreateQueueRequest.builder().queueName(TOPIC).build()
            def queueUrl = sqs.createQueue(request).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')
        when:
            sendToKafka(TOPIC, payload, ['foo': 'bar'])
        then:
            await(10, TimeUnit.SECONDS) {
                def rmr = ReceiveMessageRequest.builder().queueUrl(queueUrl).build()
                def msg = sqs.receiveMessage(rmr).messages.find {
                    it.body == payload
                }

                return msg != null
            }
    }
}

