package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.ConnectorSpec

@Slf4j
class ConnectorContainerIT extends ConnectorSpec {

    def "is healthy"() {
        when:
            def res = request.get("/q/health")
            def body = res.as(Map.class)
        then:
            res.statusCode == 200

            body.status == 'UP'
            body.checks.find {
                it.name == 'camel-readiness-checks' && it.status == 'UP'
            }
            body.checks.find {
                it.name == 'camel-liveness-checks' && it.status == 'UP'
            }
    }

    def "exposes metrics"() {
        when:
            def res = request.get("/q/metrics")
        then:
            res.statusCode == 200
    }
}
