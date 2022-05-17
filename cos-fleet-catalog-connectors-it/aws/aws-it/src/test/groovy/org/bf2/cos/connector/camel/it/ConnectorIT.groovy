package org.bf2.cos.connector.camel.it

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.aws.AWSContainer
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.cloudwatch.model.Metric
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult
import software.amazon.awssdk.services.cloudwatch.model.MetricStat
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import software.amazon.awssdk.services.kinesis.model.Record
import software.amazon.awssdk.utils.IoUtils

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static AWSContainer aws

    @Override
    def setupSpec() {
        aws = new AWSContainer(network, 'cw', 's3', 'sns', 'sqs', 'kinesis', 'dynamodb')
        aws.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(aws)
    }

    // ********************************************
    //
    // CW
    //
    // ********************************************

    def "cw sink"() {
        setup:
        def topic = UUID.randomUUID().toString()
        def namespace = 'cw-namespace'
        def group = UUID.randomUUID().toString()

        def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_CLOUDWATCH, """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - removeHeader:
                        name: "kafka.HEADERS"
                    - to:
                        uri: kamelet:aws-cloudwatch-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          cwNamespace: ${namespace}
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                """)

        cnt.start()

        def cw = aws.cw()
        when:
        kafka.send(topic, null, [ 'metric-value': '2.0', 'metric-name': 'metricName1651062317458', 'metric-unit': 'Percent' ])
        then:
        await(10, TimeUnit.SECONDS) {
            try {
                List<MetricDataResult> mdrs =  cw.getMetricData(b->b.metricDataQueries(
                                                MetricDataQuery.builder()
                                                        .id("some")
                                                        .metricStat(MetricStat.builder()
                                                                .period(60)
                                                                .stat("Sum")
                                                                .metric(Metric.builder()
                                                                        .namespace(namespace)
                                                                        .metricName("metricName1651062317458").build())
                                                                .build()
                                                        )
                                                        .build())
                                                .startTime(Instant.now().minusSeconds(3600))
                                                .endTime(Instant.now().plusSeconds(3600))
                                        ).metricDataResults();

                return mdrs.size() == 1 && mdrs.get(0).values().size() == 1 && mdrs.get(0).values().get(0).equals(2.0d)
            } catch (Exception e) {
                return false
            }
        }
        cleanup:
        closeQuietly(cnt)
    }

    // ********************************************
    //
    // S3
    //
    // ********************************************

    def "s3 sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def fileName = 'filetest.txt'
            def group = UUID.randomUUID().toString()

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_S3, """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - removeHeader:
                        name: "kafka.HEADERS"
                    - to:
                        uri: kamelet:aws-s3-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          bucketNameOrArn: ${topic}
                          autoCreateBucket: true
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                          keyName: ${fileName}
                """)

            cnt.start()

            def s3 = aws.s3()
        when:
            kafka.send(topic, payload, [ 'file': fileName])
        then:
            await(10, TimeUnit.SECONDS) {
                try {
                    return payload == IoUtils.toUtf8String(s3.getObject(b -> b.bucket(topic).key(fileName)))
                } catch (Exception e) {
                    return false
                }
            }
        cleanup:
            closeQuietly(cnt)
    }

    def "s3 source"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def fileName = 'filetest.txt'

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_S3,"""
                - route:
                    from:
                      uri: kamelet:aws-s3-source
                      parameters:
                        accessKey: ${aws.credentials.accessKeyId()}
                        secretKey: ${aws.credentials.secretAccessKey()}
                        region: ${aws.region}
                        bucketNameOrArn: ${topic}
                        autoCreateBucket: true
                        uriEndpointOverride: ${aws.endpoint}
                        overrideEndpoint: true
                    steps:
                      - to:
                          uri: kamelet:kafka-not-secured-sink
                          parameters:
                            topic: ${topic}
                            bootstrapServers: ${kafka.outsideBootstrapServers}
                """)

            cnt.start()
        when:
            aws.s3().putObject(
                b -> b.key(fileName).bucket(topic).build(),
                RequestBody.fromString(payload))
        then:
            await(10, TimeUnit.SECONDS) {
                def record = kafka.poll(cnt.containerId, topic).find {
                    it.value() == payload
                }

                return record != null
            }
        cleanup:
            closeQuietly(cnt)
    }

    // ********************************************
    //
    // SNS
    //
    // ********************************************

    def "sns sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def sqs = aws.sqs()
            def queueUrl = sqs.createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def sns = aws.sns()
            def topicArn = sns.createTopic(b -> b.name(topic)).topicArn()
            sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(queueUrl).returnSubscriptionArn(true).build())

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_SNS, """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - removeHeader:
                        name: "kafka.HEADERS"
                    - to:
                        uri: kamelet:aws-sns-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          topicNameOrArn: ${topic}
                          autoCreateTopic: true
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                """)

            cnt.start()
        when:
            kafka.send(topic, payload, ['foo': 'bar'])
        then:
            await(10, TimeUnit.SECONDS) {
                def msg = sqs.receiveMessage(b -> b.queueUrl(queueUrl))

                if (!msg.hasMessages()) {
                    return false
                }

                def msgPayload = new JsonSlurper().parseText(msg.messages().get(0).body())
                return msgPayload.Message == payload
            }
        cleanup:
            closeQuietly(cnt)
    }

    // ********************************************
    //
    // SQS
    //
    // ********************************************


    def "sqs sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def sqs = aws.sqs()
            def queueUrl = sqs.createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_SQS, """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - removeHeader:
                        name: "kafka.HEADERS"
                    - to:
                        uri: kamelet:aws-sqs-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          queueNameOrArn: ${topic}
                          amazonAWSHost: ${AWSContainer.CONTAINER_ALIAS}
                          autoCreateQueue: true
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                """)

            cnt.start()

            def s3 = aws.s3()
        when:
            kafka.send(topic, payload, ['foo': 'bar'])
        then:
            await(10, TimeUnit.SECONDS) {
                def msg = sqs.receiveMessage(b -> b.queueUrl(queueUrl)).messages.find {
                    it.body == payload
                }

                return msg != null
            }
        cleanup:
            closeQuietly(cnt)
    }

    def "sqs source"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def queueUrl = aws.sqs().createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_SQS, """
                - route:
                    from:
                      uri: kamelet:aws-sqs-source
                      parameters:
                        accessKey: ${aws.credentials.accessKeyId()}
                        secretKey: ${aws.credentials.secretAccessKey()}
                        region: ${aws.region}
                        queueNameOrArn: ${topic}
                        amazonAWSHost: ${AWSContainer.CONTAINER_ALIAS}
                        autoCreateQueue: true
                        uriEndpointOverride: ${aws.endpoint}
                        overrideEndpoint: true
                    steps:
                      - to:
                          uri: kamelet:kafka-not-secured-sink
                          parameters:
                            topic: ${topic}
                            bootstrapServers: ${kafka.outsideBootstrapServers}
                """)

            cnt.start()
        when:
            aws.sqs().sendMessage(
        b -> b.queueUrl(queueUrl).messageBody(payload)
            )
        then:
            await(10, TimeUnit.SECONDS) {
                def record = kafka.poll(cnt.containerId, topic).find {
                    it.value() == payload
                }

                return record != null
            }
        cleanup:
            closeQuietly(cnt)
    }



    // ********************************************
    //
    // Kinesis
    //
    // ********************************************

    def "kinesis sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def kinesis = aws.kinesis()

            kinesis.createStream(b -> b.streamName(topic).shardCount(1))

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_KINESIS, """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                        autoOffsetReset: "earliest"
                    steps:
                    - removeHeader:
                        name: "kafka.HEADERS"
                    - to:
                        uri: kamelet:aws-kinesis-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          stream: ${topic}
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                """)

            cnt.start()
        when:
            kafka.send(topic, payload, ['foo': 'bar'])
        then:
            await(10, TimeUnit.SECONDS) {
                String sharedIt

                try {
                    sharedIt = ConnectorSupport.getShardIterator(kinesis, topic)
                } catch (Exception e) {
                    return false
                }

                for (Record record : aws.kinesis().getRecords(b -> b.shardIterator(sharedIt)).records()) {
                    String data = new String(record.data().asByteArray())
                    if (payload == data) {
                        return true
                    }
                }
                return false
            }
        cleanup:
            closeQuietly(cnt)
    }

    def "kinesis source"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def topic = UUID.randomUUID().toString()
            def kinesis = aws.kinesis()

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_KINESIS, """
                - route:
                    from:
                      uri: kamelet:cos-aws-kinesis-source
                      parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          stream: ${topic}
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                    steps:
                      - to: kamelet:cos-encoder-bytearray-action
                      - to: log:test?showAll=true&multiline=true
                      - to:
                          uri: kamelet:kafka-not-secured-sink
                          parameters:
                            topic: ${topic}
                            bootstrapServers: ${kafka.outsideBootstrapServers}
                """)

            cnt.start()
        when:
            ConnectorSupport.createStream(kinesis, topic)

            kinesis.putRecord(
                b -> b.streamName(topic)
                    .partitionKey("test")
                    .data(SdkBytes.fromString(payload, Charset.defaultCharset()))
            )
        then:
            await(10, TimeUnit.SECONDS) {
                def record = kafka.poll(cnt.containerId, topic).find {
                    it.value() == payload
                }

                return record != null
            }
        cleanup:
            closeQuietly(cnt)
    }

    // ********************************************
    //
    // DynamoDB
    //
    // ********************************************

    def "ddb sink"() {
        setup:
            def payload = json([
                id: 1,
                year: 2022,
                title: 'title'
            ])

            def topic = UUID.randomUUID().toString()
            def group = UUID.randomUUID().toString()
            def ddb = aws.ddb()

            ddb.createTable(b -> {
                b.tableName(topic)
                b.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                b.attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.N).build())
                b.provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
            })

            def cnt = connectorContainer(ConnectorSupport.CONTAINER_IMAGE_DDB, """
                - route:
                    from:
                      uri: kamelet:cos-kafka-not-secured-source
                      parameters:
                        topic: ${topic}
                        bootstrapServers: ${kafka.outsideBootstrapServers}
                        groupId: ${group}
                    steps:
                    - to:
                        uri: "kamelet:cos-decoder-json-action"
                    - to:
                        uri: "kamelet:cos-encoder-json-action"
                    - to:
                        uri: kamelet:aws-ddb-sink
                        parameters:
                          accessKey: ${aws.credentials.accessKeyId()}
                          secretKey: ${aws.credentials.secretAccessKey()}
                          region: ${aws.region}
                          table: ${topic}
                          operation: "PutItem"
                          uriEndpointOverride: ${aws.endpoint}
                          overrideEndpoint: true
                """)

            cnt.start()
        when:
            kafka.send(topic, payload.toString(), [:])
        then:
            untilAsserted(10, TimeUnit.SECONDS) {
                def items = ddb.scan(b -> b.tableName(topic)).items()

                assert !items.isEmpty()
                assert items[0].get('id').n() == "1"
                assert items[0].get('year').n() == "2022"
                assert items[0].get('title').s() == "title"
            }
        cleanup:
            closeQuietly(cnt)
    }

}

