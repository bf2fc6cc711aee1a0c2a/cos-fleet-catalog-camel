package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import io.restassured.RestAssured
import org.bf2.cos.connector.camel.it.support.ManagedConnectorSpec

@Slf4j
class ConnectorContainerTest extends ManagedConnectorSpec {

    def "is healthy"() {
        when:
            def res = RestAssured.get("/q/health")
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
}
