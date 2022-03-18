package org.bf2.cos.connector.camel.jq;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;

import com.fasterxml.jackson.databind.node.TextNode;

@Converter(generateLoader = true)
public class JqTypeConverter {

    @Converter
    public String jsonNodeToString(TextNode node, Exchange exchange) throws Exception {
        return node.asText();
    }

    @Converter
    public byte[] jsonNodeToByteArray(TextNode node, Exchange exchange) throws Exception {
        String text = jsonNodeToString(node, exchange);

        return text != null
                ? text.getBytes(ExchangeHelper.getCharsetName(exchange))
                : null;
    }
}
