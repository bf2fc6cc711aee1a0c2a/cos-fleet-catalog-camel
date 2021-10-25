#!/bin/bash

kcat \
  -t "$KAFKA_TOPIC" \
  -b "$KAFKA_SERVER" \
  -X security.protocol=SASL_SSL \
  -X sasl.mechanisms=PLAIN \
  -X sasl.username="$KAFKA_CLIENT_ID" \
  -X sasl.password="$KAFKA_CLIENT_SECRET" \
  -J \
  -C