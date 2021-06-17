#!/bin/bash -e
#
# Copyright (c) 2018 Red Hat, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CONNECTORS_DIR=etc/connectors

for D in "${CONNECTORS_DIR}"/*; do
  CM_NAME=$(basename "${D}")
  echo "Creating configmap: ${CM_NAME}"

  if [ -n "$1" ]; then
    kubectl create configmap "${CM_NAME}" \
      --namespace "${1}" \
      --from-file="${CONNECTORS_DIR}/${CM_NAME}/" \
      --dry-run \
      -o yaml | kubectl replace -f -
  else
    kubectl create configmap "${CM_NAME}" \
      --namespace "${1}" \
      --from-file="${CONNECTORS_DIR}/${CM_NAME}/" \
      --dry-run \
      -o yaml
  fi
done
