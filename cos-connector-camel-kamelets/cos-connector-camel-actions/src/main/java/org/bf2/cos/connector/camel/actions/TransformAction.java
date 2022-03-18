package org.bf2.cos.connector.camel.actions;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ConstantExpression;
import org.bf2.cos.connector.camel.jq.JqProcessor;

public class TransformAction implements Processor, CamelContextAware {
    private CamelContext context;
    private Processor delegate;

    private String expression;
    private String type;

    public TransformAction() {
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
    public void process(Exchange exchange) throws Exception {
        if (this.delegate == null) {
            switch (this.type) {
                case "jq": {
                    JqProcessor proc = new JqProcessor();
                    proc.setExpression(expression);
                    proc.setCamelContext(getCamelContext());

                    this.delegate = proc;
                }
                    break;
                case "constant": {
                    ConstantExpression proc = new ConstantExpression();
                    proc.setExpression(expression);
                    proc.init(getCamelContext());

                    this.delegate = new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Object body = proc.evaluate(exchange, Object.class);
                            Message message = exchange.getMessage();

                            message.setBody(body);
                        }
                    };
                }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + this.type);
            }
        }

        this.delegate.process(exchange);
    }
}
