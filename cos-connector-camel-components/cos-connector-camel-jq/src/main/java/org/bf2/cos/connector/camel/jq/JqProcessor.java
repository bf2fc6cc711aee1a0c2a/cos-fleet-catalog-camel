package org.bf2.cos.connector.camel.jq;

import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.language.JqExpression;

public class JqProcessor implements Processor, CamelContextAware {

    private CamelContext camelContext;
    private JqExpression camelExpression;

    private String expression;

    public JqProcessor() {
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (this.camelExpression == null) {
            Objects.requireNonNull(this.camelContext);
            Objects.requireNonNull(this.expression);

            this.camelExpression = new JqExpression(this.expression);
            this.camelExpression.init(camelContext);
        }

        Object answer = camelExpression.evaluate(exchange, Object.class);
        exchange.getMessage().setBody(answer);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
