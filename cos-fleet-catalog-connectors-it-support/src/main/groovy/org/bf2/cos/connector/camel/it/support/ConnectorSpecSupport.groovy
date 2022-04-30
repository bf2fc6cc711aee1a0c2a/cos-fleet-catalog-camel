package org.bf2.cos.connector.camel.it.support

import groovy.util.logging.Slf4j
import org.awaitility.Awaitility
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

@Slf4j
abstract class ConnectorSpecSupport extends Specification {
    public static final String DEFAULT_APPLICATION_PROPERTIES_LOCATION = '/etc/camel/application.properties'
    public static final String DEFAULT_ROUTE_LOCATION = '/etc/camel/sources/route.yaml'

    public static final ContainerFile DEFAULT_APPLICATION_PROPERTIES = new ContainerFile(
        DEFAULT_APPLICATION_PROPERTIES_LOCATION,
        """ 
        quarkus.log.level = INFO
        quarkus.log.category."org.apache.camel".level = INFO
        quarkus.log.category."org.apache.camel.component.kafka".level = INFO
        quarkus.log.category."org.apache.kafka.clients.consumer.ConsumerConfig".level = ERROR
        quarkus.log.category."org.apache.kafka.clients.producer.ProducerConfig".level = ERROR

        camel.k.sources[0].language = yaml
        camel.k.sources[0].location = file:/etc/camel/sources/route.yaml
        camel.k.sources[0].name = route
        """
    )

    @TempDir
    Path tmpDir

    // **********************************
    //
    // Helpers
    //
    // **********************************

    ConnectorContainer connectorContainer(String name) {
        def answer = new ConnectorContainer(
                System.getProperty('it.connector.container.group').trim(),
                name.trim(),
                System.getProperty('it.connector.container.tag').trim()
        )

        return answer
    }

    void addFileToContainer(GenericContainer<?> container, String containerPath, String content) {
        var fp = PosixFilePermissions.fromString("rw-rw-rw-")

        try {
            Path f = Files.createTempFile(tmpDir, null, null, PosixFilePermissions.asFileAttribute(fp))
            Files.write(f, content.stripMargin().stripIndent().getBytes(StandardCharsets.UTF_8))

            container.addFileSystemBind(
                    f.toAbsolutePath().toString(),
                    containerPath,
                    BindMode.READ_ONLY,
                    SelinuxContext.SHARED)
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }

    static Slf4jLogConsumer logger(String name) {
        new Slf4jLogConsumer(LoggerFactory.getLogger(name))
    }

    static void await(long timeout, TimeUnit unit, Closure<Boolean> condition) {
        Awaitility.await()
                .atMost(timeout, unit)
                .pollDelay(250, TimeUnit.MILLISECONDS)
                .until(() -> condition())
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
}
