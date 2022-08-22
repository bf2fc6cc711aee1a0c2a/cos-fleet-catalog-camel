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
package org.bf2.cos.connector.camel.serdes.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

// TODO: copied from camel upstream, remove when updating to camel 3.18
@Converter(generateLoader = true)
public final class JacksonTypeConverters {
    private static final Logger LOG = LoggerFactory.getLogger(JacksonTypeConverters.class);

    private final Object lock;
    private volatile ObjectMapper defaultMapper;

    private boolean init;
    private boolean enabled;
    private boolean toPojo;
    private String moduleClassNames;

    public JacksonTypeConverters() {
        this.lock = new Object();
    }

    @Converter
    public JsonNode toJsonNode(String text, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.readTree(text);
    }

    @Converter
    public JsonNode toJsonNode(byte[] arr, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.readTree(arr);
    }

    @Converter
    public JsonNode toJsonNode(InputStream is, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.readTree(is);
    }

    @Converter
    public JsonNode toJsonNode(File file, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.readTree(file);
    }

    @Converter
    public JsonNode toJsonNode(Reader reader, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.readTree(reader);
    }

    @Converter
    public JsonNode toJsonNode(Map map, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.valueToTree(map);
    }

    @Converter
    public String toString(JsonNode node, Exchange exchange) throws Exception {
        if (node instanceof TextNode) {
            TextNode tn = (TextNode) node;
            return tn.textValue();
        }
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        // output as string in pretty mode
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    @Converter
    public byte[] toByteArray(JsonNode node, Exchange exchange) throws Exception {
        if (node instanceof TextNode) {
            TextNode tn = (TextNode) node;
            return tn.textValue().getBytes(StandardCharsets.UTF_8);
        }

        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.writeValueAsBytes(node);
    }

    @Converter
    public InputStream toInputStream(JsonNode node, Exchange exchange) throws Exception {
        byte[] arr = toByteArray(node, exchange);
        return new ByteArrayInputStream(arr);
    }

    @Converter
    public Map<String, Object> toMap(JsonNode node, Exchange exchange) throws Exception {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext());
        return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }

    @Converter
    public Reader toReader(JsonNode node, Exchange exchange) throws Exception {
        InputStream is = toInputStream(node, exchange);
        return new InputStreamReader(is);
    }

    @Converter(fallback = true)
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) throws Exception {

        // only do this if enabled (disabled by default)
        if (!init && exchange != null) {
            Map<String, String> globalOptions = exchange.getContext().getGlobalOptions();

            // init to see if this is enabled
            String text = globalOptions.get(JacksonConstants.ENABLE_TYPE_CONVERTER);
            if (text != null) {
                text = exchange.getContext().resolvePropertyPlaceholders(text);
                enabled = "true".equalsIgnoreCase(text);
            }

            // pojoOnly is disabled by default
            text = globalOptions.get(JacksonConstants.TYPE_CONVERTER_TO_POJO);
            if (text != null) {
                text = exchange.getContext().resolvePropertyPlaceholders(text);
                toPojo = "true".equalsIgnoreCase(text);
            }

            moduleClassNames = globalOptions.get(JacksonConstants.TYPE_CONVERTER_MODULE_CLASS_NAMES);
            init = true;
        }

        if (!enabled) {
            return null;
        }

        if (!toPojo && isNotPojoType(type)) {
            return null;
        }

        if (exchange != null) {
            ObjectMapper mapper = resolveObjectMapper(exchange.getContext());

            // favor use write/read operations as they are higher level than the
            // convertValue

            // if we want to convert to a String or byte[] then use write
            // operation
            if (String.class.isAssignableFrom(type)) {
                String out = mapper.writeValueAsString(value);
                return type.cast(out);
            } else if (byte[].class.isAssignableFrom(type)) {
                byte[] out = mapper.writeValueAsBytes(value);
                return type.cast(out);
            } else if (ByteBuffer.class.isAssignableFrom(type)) {
                byte[] out = mapper.writeValueAsBytes(value);
                return type.cast(ByteBuffer.wrap(out));
            } else if (mapper.canSerialize(type) && !Enum.class.isAssignableFrom(type)) {
                // if the source value type is readable by the mapper then use
                // its read operation
                if (String.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((String) value, type);
                } else if (byte[].class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((byte[]) value, type);
                } else if (File.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((File) value, type);
                } else if (InputStream.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((InputStream) value, type);
                } else if (Reader.class.isAssignableFrom(value.getClass())) {
                    return mapper.readValue((Reader) value, type);
                } else {
                    // fallback to generic convert value
                    return mapper.convertValue(value, type);
                }
            }
        }

        // Just return null to let other fallback converter to do the job
        return null;
    }

    /**
     * Whether the type is NOT a pojo type but only a set of simple types such as String and numbers.
     */
    private static boolean isNotPojoType(Class<?> type) {
        boolean isString = String.class.isAssignableFrom(type);
        boolean isNumber = Number.class.isAssignableFrom(type) || int.class.isAssignableFrom(type)
                || long.class.isAssignableFrom(type) || short.class.isAssignableFrom(type)
                || char.class.isAssignableFrom(type) || float.class.isAssignableFrom(type)
                || double.class.isAssignableFrom(type);
        return isString || isNumber;
    }

    private ObjectMapper resolveObjectMapper(CamelContext camelContext) throws Exception {
        Set<ObjectMapper> mappers = camelContext.getRegistry().findByType(ObjectMapper.class);
        if (mappers.size() == 1) {
            return mappers.iterator().next();
        }

        if (defaultMapper == null) {
            synchronized (lock) {
                if (defaultMapper == null) {
                    ObjectMapper mapper = new ObjectMapper();
                    if (moduleClassNames != null) {
                        for (Object o : ObjectHelper.createIterable(moduleClassNames)) {
                            Class<Module> type = camelContext.getClassResolver().resolveMandatoryClass(o.toString(),
                                    Module.class);
                            Module module = camelContext.getInjector().newInstance(type);

                            LOG.debug("Registering module: {} -> {}", o, module);
                            mapper.registerModule(module);
                        }
                    }

                    defaultMapper = mapper;
                }
            }
        }

        return defaultMapper;
    }

}