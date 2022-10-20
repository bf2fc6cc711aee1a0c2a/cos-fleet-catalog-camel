package org.bf2.cos.connector.camel.it.support;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.awaitility.Awaitility;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

public abstract class AwaitStrategy implements WaitStrategy {
    protected Duration startupTimeout = Duration.ofSeconds(60);
    protected WaitStrategyTarget target;

    @Override
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
        return this;
    }

    @Override
    public void waitUntilReady(WaitStrategyTarget target) {
        this.target = target;

        try {
            Awaitility
                    .await()
                    .pollInSameThread()
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .pollDelay(500, TimeUnit.MILLISECONDS)
                    .timeout(startupTimeout.getSeconds(), TimeUnit.SECONDS)
                    .until(this::ready);
        } catch (Exception e) {
            throw new ContainerLaunchException(
                    "Error waiting for container to be ready", e);
        }
    }

    protected abstract boolean ready();

    public static WaitStrategy waitFor(Predicate<WaitStrategyTarget> condition) {
        return new AwaitStrategy() {
            @Override
            protected boolean ready() {
                return condition.test(target);
            }
        };
    }
}
