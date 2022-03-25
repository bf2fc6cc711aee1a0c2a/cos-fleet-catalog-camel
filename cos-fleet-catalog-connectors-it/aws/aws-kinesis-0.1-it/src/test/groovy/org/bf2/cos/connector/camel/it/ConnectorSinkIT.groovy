package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.ConnectorSpec
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse
import software.amazon.awssdk.services.kinesis.model.Record
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.model.Shard

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorSinkIT extends ConnectorSpec {
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
                    uri: kamelet:aws-kinesis-sink
                    parameters:
                      accessKey: ${aws.credentials.accessKeyId()}
                      secretKey: ${aws.credentials.secretAccessKey()}
                      region: ${aws.region}
                      stream: $TOPIC
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
            def request = CreateStreamRequest.builder().streamName(TOPIC).shardCount(1).build()
            aws.kinesis().createStream(request)
        when:
            sendToKafka(TOPIC, payload, [ 'file': FILE_NAME])
        then:
            await(10, TimeUnit.SECONDS) {
                String shardIterator = getShardIterator()
                def rmr = GetRecordsRequest
                        .builder()
                        .shardIterator(shardIterator)
                        .build()
                def msg = aws.kinesis().getRecords(rmr)
                // Print records
                def checkValue = false
                for (Record record : msg.records()) {
                    SdkBytes byteBuffer = record.data();
                           checkValue = (payload == new String(byteBuffer.asByteArray()));
                }
                return checkValue
            }
    }

    private String getShardIterator() {
        def shardIterator;
        def lastShardId = null;

        // Retrieve the Shards from a Stream
        DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder()
                .streamName(TOPIC)
                .build();
        List<Shard> shards = new ArrayList<>();

        DescribeStreamResponse streamRes;
        do {
            streamRes = aws.kinesis().describeStream(describeStreamRequest);
            shards.addAll(streamRes.streamDescription().shards());

            if (shards.size() > 0) {
                lastShardId = shards.get(shards.size() - 1).shardId();
            }
        } while (streamRes.streamDescription().hasMoreShards());

        GetShardIteratorRequest itReq = GetShardIteratorRequest.builder()
                .streamName(TOPIC)
                .shardIteratorType("TRIM_HORIZON")
                .shardId(lastShardId)
                .build();

        GetShardIteratorResponse shardIteratorResult = aws.kinesis().getShardIterator(itReq);
        shardIterator = shardIteratorResult.shardIterator();
        return shardIterator
    }
}

