package org.bf2.cos.connector.camel.jq;

import org.apache.camel.CamelContext;

public final class Jq {
    private Jq() {
    }

    public static JqExpression expression(String expression) {
        return new JqExpression(expression);
    }

    public static JqExpression expression(CamelContext context, String expression) {
        JqExpression answer = new JqExpression(expression);
        answer.init(context);

        return answer;
    }
}
