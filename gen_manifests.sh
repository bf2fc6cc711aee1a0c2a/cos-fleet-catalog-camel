#!/usr/bin/env bash
#
# Generate template from .json

function print_exit() {
    echo $1
    exit 1
}

for CMD in "oc sed"; do
  hash $CMD 2>/dev/null || print_exit "Dependency ${CMD} not met"
done


DIR=$(dirname "${BASH_SOURCE[0]}")
MAVEN_OPTS=${MAVEN_OPTS:-"-Xmx3000m"}
MAVEN_ARGS=${MAVEN_ARGS:-"-V -ntp -Dhttp.keepAlive=false -e"}
BUILD=${BUILD:-false}

[ "${BUILD}" == "true" ] && ./mvnw ${MAVEN_ARGS} clean install -U

BASE_DIR=${1:-etc/kubernetes/manifests/base}
TEMPLATE=${2:-templates/cos-fleet-catalog-camel.yaml}

cat <<EOT > $TEMPLATE
apiVersion: template.openshift.io/v1
kind: Template
name: cos-fleet-catalog-camel
metadata:
  name: cos-fleet-catalog-camel
  annotations:
    openshift.io/display-name: Cos Fleet Manager Connector Catalog
    description: List of available camel connectors and metadata
objects:
EOT

echo "Overwriting template ${TEMPLATE}"

for D in "${BASE_DIR}"/connectors/*; do
  CM_NAME=$(basename "${D}")

  echo "Adding configmap ${CM_NAME} to template ${TEMPLATE}"
  echo "-" >> ${TEMPLATE}
  kubectl create configmap "${CM_NAME}" \
    --from-file="${BASE_DIR}/connectors/${CM_NAME}/" \
    --dry-run=client \
    -o yaml | sed -e 's/^/  /' >> $TEMPLATE
done


echo "Regen kustomization.yaml resources"
(cd "${BASE_DIR}" && sh ./regen.sh)
