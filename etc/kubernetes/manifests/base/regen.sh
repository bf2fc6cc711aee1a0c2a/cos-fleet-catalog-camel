#!/usr/bin/env bash

DIR=$(dirname "${BASH_SOURCE[0]}")
CONNECTORS_DIR="${DIR}/connectors"

cat /dev/null > "${DIR}/kustomization.yaml"

for D in "${CONNECTORS_DIR}"/*; do
  CM_NAME=$(basename "${D}")

  echo "${D}"
  echo "${CM_NAME}"

  kustomize edit add configmap "${CM_NAME}" \
    --disableNameSuffixHash \
    --from-file="${D}"/*
done
