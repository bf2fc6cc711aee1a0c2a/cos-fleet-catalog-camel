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

package org.bf2.cos.connector.camel.serdes.bytes;

import org.apache.camel.Exchange;
import org.bf2.cos.connector.camel.serdes.MimeType;
import org.bf2.cos.connector.camel.serdes.format.DefaultDataTypeConverter;
import org.bf2.cos.connector.camel.serdes.format.spi.DataTypeConverter;
import org.bf2.cos.connector.camel.serdes.format.spi.annotations.DataType;

/**
 * Generic binary data type uses Camel message body converter mechanism to convert content to byte array representation.
 */
@DataType(name = "application-octet-stream", mediaType = "application/octet-stream")
public class ByteArrayDataType implements DataTypeConverter {

    private static final DataTypeConverter DELEGATE = new DefaultDataTypeConverter(DataType.DEFAULT_SCHEME, "binary",
            MimeType.BINARY.type(), byte[].class);

    @Override
    public void convert(Exchange exchange) {
        DELEGATE.convert(exchange);

        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MimeType.BINARY);
    }
}
