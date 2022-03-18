package org.bf2.cos.connector.camel.jq;

import java.util.List;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.quarkiverse.jackson.jq.JqFunction;
import net.thisptr.jackson.jq.Expression;
import net.thisptr.jackson.jq.Function;
import net.thisptr.jackson.jq.PathOutput;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import net.thisptr.jackson.jq.path.Path;

public final class JqFunctions {
    private static final Logger LOGGER = LoggerFactory.getLogger(JqFunctions.class);

    public static final ThreadLocal<Exchange> EXCHANGE_LOCAL = new ThreadLocal<>();

    private JqFunctions() {
    }

    public static abstract class ExchangeAwareFunction implements Function {

        @Override
        public void apply(Scope scope, List<Expression> args, JsonNode in, Path path, PathOutput output, Version version)
                throws JsonQueryException {

            Exchange exchange = EXCHANGE_LOCAL.get();

            if (exchange != null) {
                doApply(scope, args, in, path, output, version, exchange);
            }
        }

        protected abstract void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException;
    }

    /**
     * A function that allow to retrieve an {@link org.apache.camel.Message} header value as
     * part of JQ expression evaluation.
     *
     * As example, the following JQ expression sets the {@code .name} property to the value of
     * the header named {@code CommitterName}.
     *
     * <pre>
     * {@code
     * .name = header(\"CommitterName\")"
     * }
     * </pre>
     *
     */
    @JqFunction({ Header.NAME + "/1", Header.NAME + "/2" })
    public static class Header extends ExchangeAwareFunction {
        public static final String NAME = "header";

        @Override
        protected void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException {

            args.get(0).apply(scope, in, (name) -> {
                if (args.size() == 2) {
                    args.get(1).apply(scope, in, (defval) -> {
                        extract(
                                exchange,
                                name.asText(),
                                defval.asText(),
                                output);
                    });
                } else {
                    extract(
                            exchange,
                            name.asText(),
                            null,
                            output);
                }
            });
        }

        private void extract(Exchange exchange, String headerName, String headerValue, PathOutput output)
                throws JsonQueryException {
            String header = exchange.getMessage().getHeader(headerName, headerValue, String.class);

            if (header == null) {
                output.emit(NullNode.getInstance(), null);
            } else {
                output.emit(new TextNode(header), null);
            }
        }
    }

    /**
     * A function that allow to retrieve an {@link org.apache.camel.Message} property value as
     * part of JQ expression evaluation.
     *
     * As example, the following JQ expression sets the {@code .name} property to the value of
     * the header named {@code CommitterName}.
     *
     * <pre>
     * {@code
     * .name = proeprty(\"CommitterName\")"
     * }
     * </pre>
     *
     */
    @JqFunction({ Property.NAME + "/1", Property.NAME + "/2" })
    public static class Property extends ExchangeAwareFunction {
        public static final String NAME = "property";

        @Override
        protected void doApply(
                Scope scope,
                List<Expression> args,
                JsonNode in,
                Path path,
                PathOutput output,
                Version version,
                Exchange exchange)
                throws JsonQueryException {

            args.get(0).apply(scope, in, (name) -> {
                if (args.size() == 2) {
                    args.get(1).apply(scope, in, (defval) -> {
                        extract(
                                exchange,
                                name.asText(),
                                defval.asText(),
                                output);
                    });
                } else {
                    extract(
                            exchange,
                            name.asText(),
                            null,
                            output);
                }
            });
        }

        private void extract(Exchange exchange, String propertyName, String propertyValue, PathOutput output)
                throws JsonQueryException {
            String header = exchange.getProperty(propertyName, propertyValue, String.class);

            if (header == null) {
                output.emit(NullNode.getInstance(), null);
            } else {
                output.emit(new TextNode(header), null);
            }
        }
    }
}
