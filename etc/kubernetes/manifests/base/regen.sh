#!/usr/bin/env bash

CONNECTORS_DIR="connectors"

cat /dev/null > "kustomization.yaml"

for D in "${CONNECTORS_DIR}"/*; do
  CM_NAME=$(basename "${D}")

  echo "${D}"
  echo "${CM_NAME}"

  kustomize edit add configmap "${CM_NAME}" \
    --disableNameSuffixHash \
    --from-file="${D}"/*
done
