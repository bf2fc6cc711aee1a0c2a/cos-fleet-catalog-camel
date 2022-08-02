package org.bf2.cos.connector.camel.jq;

import java.util.List;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.test.QuarkusUnitTest;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

public class JqExpressionTest {
    @RegisterExtension
    static final QuarkusUnitTest CONFIG = new QuarkusUnitTest()
            .setFlatClassPath(true)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addPackage(JqProcessor.class.getPackage()));

    @Inject
    CamelContext context;
    @Inject
    ObjectMapper mapper;

    @Test
    public void extractHeader() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(mapper.createObjectNode());
        exchange.getMessage().setHeader("CommitterName", "Andrea");

        JqProcessor processor = new JqProcessor();
        processor.setExpression("header(\"CommitterName\")");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .isString()
                .isEqualTo("Andrea");
    }

    @Test
    public void extractProperty() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(mapper.createObjectNode());
        exchange.setProperty("CommitterName", "Andrea");

        JqProcessor processor = new JqProcessor();
        processor.setExpression("property(\"CommitterName\")");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .isString()
                .isEqualTo("Andrea");
    }

    @Test
    public void extractField() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put("foo", "bar");
        node.put("baz", "bak");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression(".baz");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .isString()
                .isEqualTo("bak");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void selectArray() throws Exception {
        ArrayNode node = mapper.createArrayNode();

        var n1 = mapper.createObjectNode().with("commit");
        n1.with("commit").put("name", "Stephen Dolan");
        n1.with("commit").put("message", "Merge pull request #163 from stedolan/utf8-fixes\n\nUtf8 fixes. Closes #161");

        var n2 = mapper.createObjectNode();
        n2.with("commit").put("name", "Nicolas Williams");
        n2.with("commit").put("message", "Reject all overlong UTF8 sequences.");

        node.add(n1);
        node.add(n2);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression(".[] | { message: .commit.message, name: .commit.name} ");
        processor.setCamelContext(context);
        processor.process(exchange);

        List<JsonNode> result = exchange.getMessage().getBody(List.class);

        assertThatJson(result.get(0)).isObject().containsEntry("name", "Stephen Dolan");
        assertThatJson(result.get(1)).isObject().containsEntry("name", "Nicolas Williams");
    }

    @Test
    public void setField() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.with("commit").put("name", "Nicolas Williams");
        node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression(".commit.name = \"Andrea\"");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .inPath("$.commit.name")
                .isString()
                .isEqualTo("Andrea");
    }

    @Test
    public void setFieldFromHeader() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.with("commit").put("name", "Nicolas Williams");
        node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("CommitterName", "Andrea");
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression(".commit.name = header(\"CommitterName\")");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .inPath("$.commit.name")
                .isString()
                .isEqualTo("Andrea");
    }

    @Test
    public void setFieldFromProperty() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.with("commit").put("name", "Nicolas Williams");
        node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("CommitterName", "Andrea");
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression(".commit.name = property(\"CommitterName\")");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .inPath("$.commit.name")
                .isString()
                .isEqualTo("Andrea");
    }

    @Test
    public void removeField() throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.with("commit").put("name", "Nicolas Williams");
        node.with("commit").put("message", "Reject all overlong UTF8 sequences.");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(node);

        JqProcessor processor = new JqProcessor();
        processor.setExpression("del(.commit.name)");
        processor.setCamelContext(context);
        processor.process(exchange);

        assertThatJson(exchange.getMessage().getBody(JsonNode.class))
                .inPath("$.commit")
                .isObject()
                .containsOnlyKeys("message");
    }
}
