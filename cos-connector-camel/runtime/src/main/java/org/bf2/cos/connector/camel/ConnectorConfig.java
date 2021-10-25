package org.bf2.cos.connector.camel;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "cos.connector", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class ConnectorConfig {
}
