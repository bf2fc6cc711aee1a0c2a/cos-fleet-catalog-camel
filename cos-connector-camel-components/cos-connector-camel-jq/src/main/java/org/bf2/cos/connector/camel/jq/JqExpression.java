package org.bf2.cos.connector.camel.jq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExpressionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;

import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

public class JqExpression extends ExpressionAdapter implements ExpressionResultTypeAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(JqExpression.class);

    private final String expression;

    private Scope scope;
    private ObjectMapper objectMapper;
    private String resultTypeName;
    private Class<?> resultType;
    private boolean autoDiscoverObjectMapper;
    private String headerName;

    private JsonQuery query;

    public JqExpression(String expression) {
        this(null, expression);
    }

    public JqExpression(Scope scope, String expression) {
        this.scope = scope;
        this.expression = expression;
        this.autoDiscoverObjectMapper = true;
    }

    @Override
    public void init(CamelContext camelContext) {
        super.init(camelContext);

        if (this.scope == null) {
            this.scope = CamelContextHelper.findByType(camelContext, Scope.class);
            if (scope != null) {
                LOGGER.debug("Found single Scope in Registry to use: {}", scope);
            }
        }
        if (this.scope == null) {
            this.scope = Scope.newEmptyScope();
            LOGGER.debug("Creating new Scope to use: {}", objectMapper);
        }

        try {
            this.query = JsonQuery.compile(this.expression, Versions.JQ_1_6);
        } catch (JsonQueryException e) {
            throw new RuntimeException(e);
        }

        if (objectMapper == null) {
            // lookup if there is a single default mapper we can use
            if (isAutoDiscoverObjectMapper()) {
                objectMapper = CamelContextHelper.findByType(camelContext, ObjectMapper.class);
                if (objectMapper != null) {
                    LOGGER.debug("Found single ObjectMapper in Registry to use: {}", objectMapper);
                }
            }

            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
                LOGGER.debug("Creating new ObjectMapper to use: {}", objectMapper);
            }
        }

        if (resultTypeName != null && (resultType == null || resultType == Object.class)) {
            resultType = camelContext.getClassResolver().resolveClass(resultTypeName);
        }
        if (resultType == null || resultType == Object.class) {
            resultType = JsonNode.class;
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public boolean isAutoDiscoverObjectMapper() {
        return autoDiscoverObjectMapper;
    }

    public void setAutoDiscoverObjectMapper(boolean autoDiscoverObjectMapper) {
        this.autoDiscoverObjectMapper = autoDiscoverObjectMapper;
    }

    @Override
    public String getExpressionText() {
        return this.expression;
    }

    @Override
    public Class<?> getResultType() {
        return this.resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public boolean matches(Exchange exchange) {
        final Object value = evaluate(exchange, Object.class);

        if (value instanceof BooleanNode) {
            return ((BooleanNode) value).asBoolean();
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }

        return false;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        if (this.query == null) {
            return null;
        }

        try {
            JqFunctions.EXCHANGE_LOCAL.set(exchange);

            final List<JsonNode> outputs = new ArrayList<>(1);

            final JsonNode payload = headerName == null
                    ? exchange.getMessage().getMandatoryBody(JsonNode.class)
                    : exchange.getMessage().getHeader(headerName, JsonNode.class);

            this.query.apply(scope, payload, outputs::add);

            if (outputs.size() == 1) {
                // no need to convert output
                if (resultType == JsonNode.class) {
                    return outputs.get(0);
                }

                return this.objectMapper.convertValue(outputs.get(0), resultType);
            } else if (outputs.size() > 1) {
                // no need to convert outputs
                if (resultType == JsonNode.class) {
                    return outputs;
                }

                return outputs.stream()
                        .map(item -> this.objectMapper.convertValue(item, resultType))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        } finally {
            JqFunctions.EXCHANGE_LOCAL.remove();
        }

        return null;
    }
}
