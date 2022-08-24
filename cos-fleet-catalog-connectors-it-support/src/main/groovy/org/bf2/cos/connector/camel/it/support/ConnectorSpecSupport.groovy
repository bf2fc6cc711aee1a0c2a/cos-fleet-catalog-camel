package org.bf2.cos.connector.camel.it.support

import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.awaitility.Awaitility
import org.junit.function.ThrowingRunnable
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Specification

import java.util.concurrent.TimeUnit

@Slf4j
abstract class ConnectorSpecSupport extends Specification {
    public static final String DEFAULT_APPLICATION_PROPERTIES_LOCATION = '/etc/camel/application.properties'
    public static final String DEFAULT_ROUTE_LOCATION = '/etc/camel/sources/route.yaml'

    public static final String DEFAULT_APPLICATION_PROPERTIES =""" 
        quarkus.log.level = INFO
        quarkus.log.category."org.apache.camel".level = INFO
        quarkus.log.category."org.apache.camel.component.kafka".level = INFO
        quarkus.log.category."org.apache.kafka.clients.consumer.ConsumerConfig".level = ERROR
        quarkus.log.category."org.apache.kafka.clients.producer.ProducerConfig".level = ERROR

        camel.k.sources[0].language = yaml
        camel.k.sources[0].location = file:/etc/camel/sources/route.yaml
        camel.k.sources[0].name = route
        """

    // **********************************
    //
    // Helpers
    //
    // **********************************

    static Slf4jLogConsumer logger(String name) {
        new Slf4jLogConsumer(LoggerFactory.getLogger(name))
    }

    static void await(long timeout, TimeUnit unit, Closure<Boolean> condition) {
        until(timeout, unit, condition)
    }

    static void await(long timeout, long poll, TimeUnit unit, Closure<Boolean> condition) {
        Awaitility.await()
                .atMost(timeout, unit)
                .pollDelay(poll, unit)
                .until(() -> condition())
    }

    static void until(long timeout, TimeUnit unit, Closure<Boolean> condition) {
        Awaitility.await()
                .atMost(timeout, unit)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .until(() -> condition())
    }

    static void untilAsserted(long timeout, TimeUnit unit, ThrowingRunnable condition) {
        Awaitility.await()
                .atMost(timeout, unit)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> condition())
    }

    static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return
        }

        try {
            closeable.close()
        } catch (Exception e) {
            log.debug('Failed to close {}', closeable, e)
        }
    }

    static ContainerFile route(String content) {
        return new ContainerFile(
            DEFAULT_ROUTE_LOCATION,
            content)
    }

    static String json(Object content) {
        new JsonBuilder(content).toString()
    }

    static boolean hasEnv(String envName) {
        String value = System.getenv(envName)

        if (value == null) {
            return
        }

        return value.trim().length() != 0
    }
}
