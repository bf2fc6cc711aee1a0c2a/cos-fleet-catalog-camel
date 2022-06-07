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
            def topic = topic()
            def namespace = 'cw-namespace'

            def cnt = connectorContainer('aws_cloudwatch_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_cw_namespace': namespace
            ])

            cnt.start()

            def cw = aws.cw()
        when:
            kafka.send(topic, null, [ 'metric-value': '2.0', 'metric-name': 'metricName1651062317458', 'metric-unit': 'Percent' ])
        then:
            await(10, TimeUnit.SECONDS) {
                try {
                    List<MetricDataResult> mdrs =  cw.getMetricData(b -> b.metricDataQueries(
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
            def topic = topic()
            def fileName = 'filetest.txt'

            def cnt = connectorContainer('aws_s3_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_bucket_name_or_arn': topic,
                'aws_auto_create_bucket': 'true',
                'aws_key_name': fileName,
            ])

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
            def topic =topic()
            def fileName = 'filetest.txt'

            def cnt = connectorContainer('aws_s3_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_bucket_name_or_arn': topic,
                'aws_auto_create_bucket': 'true'
            ])

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
            def topic = topic()
            def sqs = aws.sqs()
            def queueUrl = sqs.createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def sns = aws.sns()
            def topicArn = sns.createTopic(b -> b.name(topic)).topicArn()
            sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(queueUrl).returnSubscriptionArn(true).build())

            def cnt = connectorContainer('aws_sns_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_topic_name_or_arn': topic,
                'aws_auto_create_topic': 'true'
            ])

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
            def topic = topic()
            def sqs = aws.sqs()
            def queueUrl = sqs.createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def cnt = connectorContainer('aws_sqs_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_queue_name_or_arn': topic,
                'aws_auto_create_queue': 'true',
                'aws_amazon_aws_host': AWSContainer.CONTAINER_ALIAS
            ])

            cnt.start()
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
            def topic = topic()
            def queueUrl = aws.sqs().createQueue(b -> b.queueName(topic)).queueUrl().replace(AWSContainer.CONTAINER_ALIAS, 'localhost')

            def cnt = connectorContainer('aws_sqs_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_queue_name_or_arn': topic,
                'aws_auto_create_queue': 'true',
                'aws_amazon_aws_host': AWSContainer.CONTAINER_ALIAS
            ])

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
            def topic = topic()
            def kinesis = aws.kinesis()

            kinesis.createStream(b -> b.streamName(topic).shardCount(1))

            def cnt = connectorContainer('aws_kinesis_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_stream': topic
            ])

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
            def topic = topic()
            def kinesis = aws.kinesis()

            ConnectorSupport.createStream(kinesis, topic)

            def cnt = connectorContainer('aws_kinesis_source_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'aws_access_key': aws.credentials.accessKeyId(),
                    'aws_secret_key': aws.credentials.secretAccessKey(),
                    'aws_region': aws.region,
                    'aws_uri_endpoint_override': aws.endpoint,
                    'aws_override_endpoint': 'true',
                    'aws_stream': topic
            ])

            cnt.start()
        when:
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

            def topic = topic()
            def ddb = aws.ddb()

            ddb.createTable(b -> {
                b.tableName(topic)
                b.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                b.attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.N).build())
                b.provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
            })

            def cnt = connectorContainer('aws_ddb_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'aws_access_key': aws.credentials.accessKeyId(),
                'aws_secret_key': aws.credentials.secretAccessKey(),
                'aws_region': aws.region,
                'aws_uri_endpoint_override': aws.endpoint,
                'aws_override_endpoint': 'true',
                'aws_table': topic,
                'aws_operation': 'PutItem'
            ])

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

