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

package org.bf2.cos.connector.camel.serdes.avro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.bf2.cos.connector.camel.serdes.MimeType;
import org.bf2.cos.connector.camel.serdes.Serdes;
import org.bf2.cos.connector.camel.serdes.format.spi.DataTypeConverter;
import org.bf2.cos.connector.camel.serdes.format.spi.annotations.DataType;
import org.bf2.cos.connector.camel.serdes.json.Json;

/**
 * Data type uses Jackson Avro data format to marshal given JsonNode in Exchange body to a binary (byte array) representation.
 * Uses given Avro schema from the Exchange properties when marshalling the payload (usually already resolved via schema
 * resolver Kamelet action).
 */
@DataType(name = "avro-binary", mediaType = "avro/binary")
public class AvroBinaryDataType implements DataTypeConverter {

    @Override
    public void convert(Exchange exchange) {
        AvroSchema schema = exchange.getProperty(Serdes.CONTENT_SCHEMA, AvroSchema.class);

        if (schema == null) {
            throw new CamelExecutionException("Missing proper avro schema for data type processing", exchange);
        }

        try {
            byte[] marshalled = Avro.MAPPER.writer().forType(JsonNode.class).with(schema)
                    .writeValueAsBytes(exchange.getMessage().getBody());
            exchange.getMessage().setBody(marshalled);

            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MimeType.AVRO.type());
            exchange.getMessage().setHeader(Serdes.CONTENT_SCHEMA,
                    exchange.getProperty(Serdes.CONTENT_SCHEMA, "", String.class));
        } catch (JsonProcessingException e) {
            throw new CamelExecutionException("Failed to apply Json output data type on exchange", exchange, e);
        }
    }
}
