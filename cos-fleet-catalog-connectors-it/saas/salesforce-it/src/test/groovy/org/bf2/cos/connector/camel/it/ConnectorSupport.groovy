package org.bf2.cos.connector.camel.it

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets


@Slf4j
final class ConnectorSupport {
    static final String CONTAINER_IMAGE = 'cos-connector-salesforce'



    static Object query(String statement) {
        def l = login()
        def q = URLEncoder.encode(statement, StandardCharsets.UTF_8)
        def u = "${l.instance_url}/services/data/v55.0/query/?q=${q}"

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(u))
                .header('Authorization', "Bearer ${l.access_token}")
                .GET()
                .build()

        return send(request)
    }

    static Object login() {
        def params = [
                'grant_type': 'password',
                'client_id': System.getenv('SF_CLIENT_ID'),
                'client_secret': System.getenv('SF_CLIENT_SECRET'),
                'username': System.getenv('SF_CLIENT_USERNAME'),
                'password': System.getenv('SF_CLIENT_PASSWORD')
        ].inject([]) { result, entry ->
            result << "${entry.key}=${URLEncoder.encode(entry.value, StandardCharsets.UTF_8)}"
        }.join('&')


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create('https://login.salesforce.com/services/oauth2/token'))
                .headers('Content-Type', 'application/x-www-form-urlencoded')
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build()

        return send(request)
    }

    static Object send(HttpRequest request) {
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            return null
        }

        log.info(response.body())

        return new JsonSlurper().parse(response.body().getBytes(StandardCharsets.UTF_8))
    }
}
