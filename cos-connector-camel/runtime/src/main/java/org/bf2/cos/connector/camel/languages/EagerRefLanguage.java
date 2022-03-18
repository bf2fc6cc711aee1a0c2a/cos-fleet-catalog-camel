package org.bf2.cos.connector.camel.languages;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.ref.RefLanguage;
import org.apache.camel.support.PredicateToExpressionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EagerRefLanguage extends RefLanguage {
    private static final Logger LOGGER = LoggerFactory.getLogger(EagerRefLanguage.class);

    @Override
    public Expression createExpression(final String expression) {
        Expression exp = getCamelContext().getRegistry().lookupByNameAndType(expression, Expression.class);
        if (exp != null) {
            LOGGER.debug("Found and instance of {} expression in the registry {}", expression, exp);
            exp.init(getCamelContext());
            return exp;
        }

        Predicate pred = getCamelContext().getRegistry().lookupByNameAndType(expression, Predicate.class);
        if (pred != null) {
            LOGGER.debug("Found and instance of {} predicate in the registry {}", expression, pred);
            pred.init(getCamelContext());
            return PredicateToExpressionAdapter.toExpression(pred);
        }

        return super.createExpression(expression);
    }
}
