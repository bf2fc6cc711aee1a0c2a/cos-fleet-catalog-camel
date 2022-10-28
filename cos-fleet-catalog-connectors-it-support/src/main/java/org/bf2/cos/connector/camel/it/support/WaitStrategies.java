package org.bf2.cos.connector.camel.it.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.internal.ExternalPortListeningCheck;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

public final class WaitStrategies {
    private WaitStrategies() {
    }

    public static WaitStrategy forListeningPort() {
        return new ExternalPort();
    }

    public static WaitStrategy forHealth(int port) {
        return Wait.forHttp("/q/health").forPort(port).forStatusCode(200);
    }

    public static WaitStrategy forHttpEndpoint(String scheme, int port) {
        return new Http(scheme, port, null);
    }

    public static WaitStrategy forHttpEndpoint(String scheme, int port, String path) {
        return new Http(scheme, port, path);
    }

    public static class ExternalPort extends AbstractWaitStrategy {
        private static final Logger LOGGER = LoggerFactory.getLogger(ExternalPort.class);

        @Override
        protected void waitUntilReady() {
            final Set<Integer> externalLivenessCheckPorts = getLivenessCheckPorts();

            if (externalLivenessCheckPorts.isEmpty()) {
                LOGGER.debug("Liveness check ports of {} is empty. Not waiting.",
                        waitStrategyTarget.getContainerInfo().getName());
                return;
            }

            Callable<Boolean> externalCheck = new ExternalPortListeningCheck(waitStrategyTarget, externalLivenessCheckPorts);

            try {
                Instant now = Instant.now();

                Awaitility.await()
                        .pollInterval(Duration.ofMillis(100))
                        .pollDelay(Duration.ZERO)
                        .timeout(startupTimeout.getSeconds(), TimeUnit.SECONDS)
                        .ignoreExceptions()
                        .until(externalCheck);

                LOGGER.debug(
                        "External port check passed for {} in {}",
                        externalLivenessCheckPorts,
                        Duration.between(now, Instant.now()));

            } catch (CancellationException e) {
                throw new ContainerLaunchException("Timed out waiting for container port to open (" +
                        waitStrategyTarget.getHost() +
                        " ports: " +
                        externalLivenessCheckPorts +
                        " should be listening)");
            }
        }
    }

    public static class Http extends AwaitStrategy {
        private final String scheme;
        private final int port;
        private final String path;

        public Http(String scheme, int port, String path) {
            Objects.requireNonNull(scheme);

            this.scheme = scheme;
            this.port = port;

            if (path == null) {
                path = "";
            } else if (!path.startsWith("/")) {
                path = "/" + path;
            }

            this.path = path;
        }

        @Override
        public boolean ready() {
            try {
                String uri = String.format(
                        "%s://%s:%d%s",
                        scheme,
                        target.getHost(),
                        target.getMappedPort(port),
                        path);

                var request = HttpRequest.newBuilder()
                        .uri(new URI(uri))
                        .GET()
                        .build();

                var result = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                return result.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
