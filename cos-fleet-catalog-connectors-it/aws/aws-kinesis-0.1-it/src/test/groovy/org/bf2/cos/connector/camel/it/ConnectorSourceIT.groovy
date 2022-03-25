package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorSourceIT extends ConnectorSpec {
    static String TOPIC = 'foo'
    static String FILE_NAME = 'filetest.txt'

    AWSContainer aws

    def doSetup() {
        this.aws = new AWSContainer(network, 'kinesis')
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
                  uri: kamelet:aws-kinesis-source
                  parameters:
                      accessKey: ${aws.credentials.accessKeyId()}
                      secretKey: ${aws.credentials.secretAccessKey()}
                      region: ${aws.region}
                      stream: $TOPIC
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
            def kinesis = aws.kinesis()
            createStreamAndWait(kinesis)
        when:
            kinesis.putRecord(
                    PutRecordRequest.builder()
                    .streamName(TOPIC)
                    .partitionKey("test")
                    .data(SdkBytes.fromString(payload, Charset.defaultCharset()))
                    .build()
            )
        then:
            await(10, TimeUnit.SECONDS) {
                def record = readFromKafka(TOPIC).find {
                    it.value() == payload
                }

                return record != null
            }
    }

    private void createStreamAndWait(KinesisClient kinesis) {
        def createRequest = CreateStreamRequest.builder().streamName(TOPIC).shardCount(1).build()
        kinesis.createStream(createRequest)
        DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder().streamName(TOPIC).build();
        String status;
        do {
            status = aws.kinesis().describeStream(describeStreamRequest).streamDescription().streamStatus()
        } while (!status.equals("ACTIVE"));
    }
}

