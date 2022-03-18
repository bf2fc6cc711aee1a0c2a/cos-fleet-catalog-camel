package org.bf2.cos.connector.camel.actions;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.bf2.cos.connector.camel.jq.JqExpression;

public class FilterAction implements Predicate, CamelContextAware {
    private CamelContext context;
    private JqExpression delegate;

    private String expression;
    private String type;

    public FilterAction() {
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

    public String getExpression() {
        return this.expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean matches(Exchange exchange) {
        if (this.delegate == null) {
            switch (this.type) {
                case "jq": {
                    JqExpression exp = new JqExpression(expression);
                    exp.init(getCamelContext());

                    this.delegate = exp;
                }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + this.type);
            }
        }

        return this.delegate.matches(exchange);
    }
}
