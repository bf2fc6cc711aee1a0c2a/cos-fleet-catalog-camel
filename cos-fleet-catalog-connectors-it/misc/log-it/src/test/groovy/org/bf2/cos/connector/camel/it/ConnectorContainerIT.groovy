package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.SimpleConnectorSpec
import spock.lang.Unroll

@Slf4j
class ConnectorContainerIT extends SimpleConnectorSpec {

    @Unroll
    def "container #image exposes health and metrics"(String image) {
        setup:
            def cnt = connectorContainer(image)
            cnt.start()
        when:
            def health = cnt.request.get('/q/health')
            def metrics = cnt.request.get("/q/metrics")
        then:
            health.statusCode == 200
            metrics.statusCode == 200

            with (health.as(Map.class)) {
                status == 'UP'
                checks.find {
                    it.name == 'camel-readiness-checks' && it.status == 'UP'
                }
                checks.find {
                    it.name == 'camel-liveness-checks' && it.status == 'UP'
                }
            }
        cleanup:
            closeQuietly(cnt)
        where:
            image << [
                'cos-connector-log',
            ]
    }
}
