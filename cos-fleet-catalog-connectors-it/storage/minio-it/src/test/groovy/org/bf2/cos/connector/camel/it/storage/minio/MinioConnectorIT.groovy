package org.bf2.cos.connector.camel.it.storage.minio

import io.minio.GetObjectArgs
import io.minio.PutObjectArgs
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MinioConnectorIT extends KafkaConnectorSpec {

    static MinioContainer minio

    @Override
    def setupSpec() {
        minio = new MinioContainer(network)
        minio.start()
    }

    @Override
    def cleanupSpec() {
        closeQuietly(minio)
    }

    def "minio sink"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def objectName = UUID.randomUUID().toString()
            def topic = topic()
            def getArgs = GetObjectArgs.builder().bucket(topic).object(objectName).build()

            def cnt = connectorContainer('minio_sink_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    'minio_access_key': minio.accessKey,
                    'minio_secret_key': minio.secretKey,
                    'minio_endpoint': "http://" + MinioContainer.CONTAINER_ALIAS + ":" + MinioContainer.PORT,
                    'minio_bucket_name': topic,
                    'minio_auto_create_bucket': 'true'
            ])

            cnt.start()

            def mc = minio.client()
        when:
            kafka.send(topic, payload, [ 'file': objectName])
        then:
            await(10, TimeUnit.SECONDS) {
                try {
                    def stream = mc.getObject(getArgs)
                    def result = new String(stream.readAllBytes(), StandardCharsets.UTF_8)

                    return payload == result
                } catch (Exception e) {
                    return false
                }
            }
        cleanup:
            closeQuietly(cnt)
    }

    def "minio source"() {
        setup:
            def payload = '''{ "username":"oscerd", "city":"Rome" }'''
            def objectName = UUID.randomUUID().toString()
            def topic = topic()
            def getArgs = GetObjectArgs.builder().bucket(topic).object(objectName).build()

            def cnt = connectorContainer('minio_source_0.1.json', [
                    'kafka_topic' : topic,
                    'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                    'kafka_consumer_group': UUID.randomUUID().toString(),
                    'minio_access_key': minio.accessKey,
                    'minio_secret_key': minio.secretKey,
                    'minio_endpoint': "http://" + MinioContainer.CONTAINER_ALIAS + ":" + MinioContainer.PORT,
                    'minio_bucket_name': topic,
                    'minio_auto_create_bucket': 'true',
                    'minio_delete_after_read': 'true'
            ])

            cnt.start()
        when:
            try (InputStream is = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))) {
                def object = PutObjectArgs.builder()
                        .bucket(topic)
                        .object(objectName)
                        .stream(is, is.available(), -1)
                        .build()

                minio.client().putObject(object)
            }
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

}
