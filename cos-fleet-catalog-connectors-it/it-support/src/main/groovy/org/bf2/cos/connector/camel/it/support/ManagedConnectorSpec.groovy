package org.bf2.cos.connector.camel.it.support

import groovy.util.logging.Slf4j
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

@Slf4j
@Testcontainers
abstract class ManagedConnectorSpec extends Specification {
    @Shared
    ManagedConnectorContainer mctr = new ManagedConnectorContainer()

    def setup() {
        RestAssured.baseURI = "http://${mctr.serviceAddress}"
        RestAssured.port = mctr.servicePort

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build()
    }
}
