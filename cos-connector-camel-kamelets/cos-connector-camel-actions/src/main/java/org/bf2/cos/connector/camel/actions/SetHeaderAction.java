package org.bf2.cos.connector.camel.actions;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.support.ExpressionAdapter;
import org.bf2.cos.connector.camel.jq.JqExpression;

public class SetHeaderAction extends ExpressionAdapter implements CamelContextAware {
    private CamelContext context;
    private Expression delegate;

    private String expression;
    private String type;

    public SetHeaderAction() {
    }

    @Override
    public void init(CamelContext context) {
        super.init(context);
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
    public Object evaluate(Exchange exchange) {
        if (this.delegate == null) {
            switch (this.type) {
                case "jq": {
                    JqExpression expr = new JqExpression(expression);
                    expr.init(context);

                    this.delegate = expr;
                }
                    break;
                case "constant": {
                    ConstantExpression expr = new ConstantExpression();
                    expr.setExpression(expression);
                    expr.init(context);

                    this.delegate = expr;
                }
                    break;
            }
        }
        if (this.delegate == null) {
            return null;
        }

        return this.delegate.evaluate(exchange, Object.class);
    }
}
