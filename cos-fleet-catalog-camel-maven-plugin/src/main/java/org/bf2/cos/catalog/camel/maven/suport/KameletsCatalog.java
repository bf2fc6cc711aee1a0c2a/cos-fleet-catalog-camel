/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bf2.cos.catalog.camel.maven.suport;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.apache.maven.plugin.logging.Log;

public final class KameletsCatalog {
    private static final String KAMELETS_DIR = "kamelets";

    private final Map<String, ObjectNode> models;

    public KameletsCatalog(ClassLoader cl, Log log) {
        this.models = loadCatalog(cl, log);
    }

    private static Map<String, ObjectNode> loadCatalog(ClassLoader cl, Log log) {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        final Map<String, ObjectNode> answer = new HashMap<>();

        try (ScanResult scanResult = new ClassGraph().addClassLoader(cl).acceptPaths("/" + KAMELETS_DIR + "/").scan()) {
            for (Resource resource : scanResult.getAllResources()) {
                try {
                    final ObjectNode content = mapper.readValue(resource.open(), ObjectNode.class);
                    final String apiVersion = content.requiredAt("/apiVersion").asText();
                    final String kind = content.requiredAt("/kind").asText();
                    final String name = content.requiredAt("/metadata/name").asText();

                    answer.put(
                            String.format("%s:%s:%s", apiVersion, kind, name),
                            mapper.readValue(resource.open(), ObjectNode.class));
                } catch (IOException e) {
                    log.warn("Cannot init Kamelet Catalog with content of " + resource.getPath(), e);
                }

            }
        }

        return Collections.unmodifiableMap(answer);
    }

    public static String type(ObjectNode node) {
        return node.requiredAt("/metadata/labels").get("camel.apache.org/kamelet.type").asText();
    }

    public static String name(ObjectNode node) {
        return node.requiredAt("/metadata/name").asText();
    }

    public static String version(ObjectNode node) {
        return node.requiredAt("/metadata/labels").get("camel.apache.org/kamelet.version").asText();
    }

    public Map<String, ObjectNode> getKamelets() {
        return models;
    }

    public ObjectNode kamelet(JsonNode node) {
        return kamelet(node.required("name").asText(), node.required("version").asText());
    }

    public ObjectNode kamelet(String name, String version) {
        return getKamelets().entrySet().stream()
                .filter(k -> Objects.equals(name, name(k.getValue())) && Objects.equals(version, version(k.getValue())))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to find kamelet with name " + name + "and version " + version));
    }

    public Stream<Map.Entry<String, ObjectNode>> kamelets() {
        return getKamelets().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(k -> !Objects.equals("action", type(k.getValue())));
    }

    public Stream<Map.Entry<String, ObjectNode>> actions() {
        return getKamelets().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(k -> Objects.equals("action", type(k.getValue())));
    }
}