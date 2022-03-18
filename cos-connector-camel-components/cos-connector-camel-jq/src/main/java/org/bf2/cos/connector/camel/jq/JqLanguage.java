package org.bf2.cos.connector.camel.jq;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;

@Language("jq")
public class JqLanguage extends LanguageSupport implements StaticService {

    private Class<?> resultType;
    private String headerName;

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression) {
        JqExpression exp = new JqExpression(expression);
        exp.setResultType(resultType);
        exp.setHeaderName(headerName);
        exp.init(getCamelContext());

        return exp;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JqExpression answer = new JqExpression(expression);
        answer.setResultType(property(Class.class, properties, 0, resultType));
        answer.setHeaderName(property(String.class, properties, 1, headerName));
        answer.init(getCamelContext());

        return answer;
    }
}
