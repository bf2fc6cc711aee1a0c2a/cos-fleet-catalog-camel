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
package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;

import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.YAML_MAPPER;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.getClassLoader;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.kameletName;
import static org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport.kameletVersion;

public final class KameletsCatalog {
    private static final String KAMELETS_DIR = "kamelets";
    private static final String KAMELETS_FILE_SUFFIX = ".kamelet.yaml";

    private final List<ObjectNode> kamelets;

    private KameletsCatalog(List<ObjectNode> kamelets) {
        this.kamelets = kamelets != null
                ? Collections.unmodifiableList(kamelets)
                : Collections.emptyList();
    }

    public List<ObjectNode> getKamelets() {
        return kamelets;
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

    public static KameletsCatalog get(MavenProject project, Log log) throws Exception {
        List<ObjectNode> answer = load(getClassLoader(project), log);
        return new KameletsCatalog(answer);
    }

    private static List<ObjectNode> load(ClassLoader cl, Log log) throws Exception {
        final ClassGraph cg = new ClassGraph().addClassLoader(cl).acceptPaths("/" + KAMELETS_DIR + "/");
        final List<ObjectNode> answer = new ArrayList<>();

        try (ScanResult scanResult = cg.scan()) {
            for (Resource resource : scanResult.getAllResources()) {
                resource.getPath();

                ObjectNode kamelet = YAML_MAPPER.readValue(resource.open(), ObjectNode.class);
                if (kamelet.at("/metadata/name").isMissingNode()) {
                    String name = resource.getPath();

                    int index = resource.getPath().lastIndexOf(KAMELETS_FILE_SUFFIX);
                    if (index > 0) {
                        name = name.substring(0, index);
                    }

                    kamelet.with("metadata").put("name", name.substring(9));
                }

                log.debug("Found"
                        + " kamelet " + kameletName(kamelet) + ":" + kameletVersion(kamelet)
                        + " at " + resource.getClasspathElementURI());

                answer.add(kamelet);
            }
        }

        return answer;
    }
}