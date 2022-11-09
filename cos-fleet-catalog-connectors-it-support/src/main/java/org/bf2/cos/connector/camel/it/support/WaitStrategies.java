package org.bf2.cos.connector.camel.it.support;

import java.time.Duration;
import java.time.Instant;
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
        return new WaitStrategies.ExternalPort();
    }

    public static WaitStrategy forHealth(int port) {
        return Wait.forHttp("/q/health").forPort(port).forStatusCode(200);
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
}
