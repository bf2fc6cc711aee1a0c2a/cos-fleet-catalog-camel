package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.utils.IoUtils
import spock.lang.Ignore

import java.util.concurrent.TimeUnit

@Ignore("Failing on CI")
@Slf4j
class ConnectorSinkIT extends ConnectorSpec {
    static String TOPIC = 'foo'
    static String FILE_NAME = 'filetest.txt'

    AWSContainer aws

    def doSetup() {
        this.aws = new AWSContainer(network, 's3')
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
                    uri: kamelet:aws-s3-sink
                    parameters:
                      accessKey: ${aws.credentials.accessKeyId()}
                      secretKey: ${aws.credentials.secretAccessKey()}
                      region: ${aws.region}
                      bucketNameOrArn: $TOPIC
                      autoCreateBucket: true
                      uriEndpointOverride: ${aws.endpoint}
                      overrideEndpoint: true
                      keyName: $FILE_NAME
            """)
    }

    def doCleanup() {
        closeQuietly(this.aws)
    }

    def "sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def s3 = aws.s3()
        when:
            sendToKafka(TOPIC, payload, [ 'file': FILE_NAME])
        then:
            await(10, TimeUnit.SECONDS) {
                def rmr = GetObjectRequest.builder().bucket(TOPIC).key(FILE_NAME).build()
                def msg = IoUtils.toUtf8String(s3.getObject(rmr))
                return msg == payload
            }
    }
}

