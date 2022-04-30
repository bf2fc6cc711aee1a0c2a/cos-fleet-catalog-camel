package org.bf2.cos.connector.camel.it

import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse
import software.amazon.awssdk.services.kinesis.model.Shard

final class ConnectorSupport {
    static final String CONTAINER_IMAGE_CLOUDWATCH = 'cos-connector-aws-cloudwatch'
    static final String CONTAINER_IMAGE_DDB = 'cos-connector-aws-ddb'
    static final String CONTAINER_IMAGE_DDB_STREAMS = 'cos-connector-aws-ddb-streams'
    static final String CONTAINER_IMAGE_KINESIS = 'cos-connector-aws-kinesis'
    static final String CONTAINER_IMAGE_LAMBDA = 'cos-connector-aws-lambda'
    static final String CONTAINER_IMAGE_S3 = 'cos-connector-aws-s3'
    static final String CONTAINER_IMAGE_SES = 'cos-connector-aws-ses'
    static final String CONTAINER_IMAGE_SNS = 'cos-connector-aws-sns'
    static final String CONTAINER_IMAGE_SQS = 'cos-connector-aws-sqs'


    static String getShardIterator(KinesisClient kinesis, String topic) {
        def lastShardId = null

        DescribeStreamResponse describeStreamResponse;

        do {
            describeStreamResponse = kinesis.describeStream(b -> b.streamName(topic))
            List<Shard> shards = describeStreamResponse.streamDescription().shards();

            if (shards != null && shards.size() > 0) {
                lastShardId = shards.get(shards.size() - 1).shardId();
            }
        } while (describeStreamResponse.streamDescription().hasMoreShards());

        return kinesis
                .getShardIterator(b -> b.streamName(topic).shardIteratorType("TRIM_HORIZON").shardId(lastShardId))
                .shardIterator()
    }

    static void createStream(KinesisClient kinesis, String topic) {
        kinesis.createStream(b -> b.streamName(topic).shardCount(1))

        String status
        do {
            status = kinesis.describeStream(b -> b.streamName(topic)).streamDescription().streamStatus()
        } while (status != "ACTIVE");
    }
}
