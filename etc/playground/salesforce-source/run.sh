#!/bin/bash

PLAYGROUND_ROOT="$(dirname "${BASH_SOURCE}")"

export CAMEL_K_SOURCES="${PLAYGROUND_ROOT}"
export CAMEL_K_CONF="${PLAYGROUND_ROOT}/connector.properties"
export CAMEL_K_CONF_D="${PLAYGROUND_ROOT}/conf.d"
export CAMEL_K_MOUNT_PATH_CONFIGMAPS="$CAMEL_K_CONF_D/_configmaps"
export CAMEL_K_MOUNT_PATH_SECRETS="$CAMEL_K_CONF_D/_secrets"

export CONNECTOR_GROUP="saas"
export CONNECTOR_ID="salesforce-0.1"
export CONNECTOR_RUNNER="${PLAYGROUND_ROOT}/../../../cos-fleet-catalog-connectors/$CONNECTOR_GROUP/$CONNECTOR_ID/target/connector-runner.jar"

mvn -f "${PLAYGROUND_ROOT}/../../../pom.xml" -pl ":${CONNECTOR_ID}" -am -Pfat-jar clean package

exec java -jar "$CONNECTOR_RUNNER"