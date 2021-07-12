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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;

import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.YAML_MAPPER;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletName;
import static org.bf2.cos.catalog.camel.maven.suport.CatalogSupport.kameletVersion;

public final class KameletsCatalog {
    private static final String KAMELETS_DIR = "kamelets";
    private static final String KAMELETS_FILE_SUFFIX = ".kamelet.yaml";

    private final List<ObjectNode> models;

    public KameletsCatalog(ClassLoader cl) throws IOException {
        final List<ObjectNode> kamelets = new ArrayList<>();
        final ClassGraph cg = new ClassGraph().addClassLoader(cl).acceptPaths("/" + KAMELETS_DIR + "/");

        try (ScanResult scanResult = cg.scan()) {
            for (Resource resource : scanResult.getAllResources()) {
                ObjectNode kamelet = YAML_MAPPER.readValue(resource.open(), ObjectNode.class);
                if (kamelet.at("/metadata/name").isMissingNode()) {
                    String name = resource.getPath();

                    int index = resource.getPath().lastIndexOf(KAMELETS_FILE_SUFFIX);
                    if (index > 0) {
                        name = name.substring(0, index);
                    }

                    kamelet.with("metadata").put("name", name.substring(9));
                }

                kamelets.add(kamelet);
            }
        }

        this.models = Collections.unmodifiableList(kamelets);
    }

    public List<ObjectNode> getKamelets() {
        return models;
    }

    public ObjectNode kamelet(String name, String version) {
        List<ObjectNode> kamelets = getKamelets().stream()
                .filter(node -> Objects.equals(name, kameletName(node)))
                .filter(node -> Objects.equals(version, kameletVersion(node)))
                .collect(Collectors.toList());

        if (kamelets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unable to find kamelet with name " + name + " and version " + version);
        }
        if (kamelets.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple kamelet with name " + name + " and version " + version);
        }

        return kamelets.get(0);
    }
}