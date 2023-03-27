package org.bf2.cos.catalog.camel.maven.connector.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.l2x6.pom.tuner.PomTransformer;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public final class Builders {
    private Builders() {
    }

    public static class Resource {
        private Path path;
        private String template;
        private Map<String, Object> templateParams;

        public Resource() {
            this.templateParams = new HashMap<>();
        }

        public Resource withPath(String first, String... more) {
            this.path = Paths.get(first, more);
            return this;
        }

        public Resource withTemplate(String template) {
            this.template = template;
            return this;
        }

        public Resource withTemplateParam(String key, Object val) {
            this.templateParams.put(key, val);
            return this;
        }

        public Resource withTemplateParams(Map<String, Object> params) {
            this.templateParams.putAll(params);
            return this;
        }

        public void build() throws IOException {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            if (!Files.exists(path) && this.template != null) {
                try (InputStream is = getClass().getResourceAsStream(this.template)) {
                    if (is == null) {
                        throw new IOException("Unable to find template " + this.template);
                    }

                    MustacheFactory mf = new DefaultMustacheFactory();
                    Writer writer = new StringWriter();

                    Mustache mustache = mf.compile(new InputStreamReader(is), this.template);
                    mustache.execute(writer, this.templateParams);

                    Files.write(path, writer.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    public static class Pom {
        private Path path;
        private String template;
        private Map<String, Object> templateParams;
        private List<PomTransformer.Transformation> transformations;

        public Pom() {
            this.templateParams = new HashMap<>();
            this.transformations = new ArrayList<>();
        }

        public Pom withPath(String first, String... more) {
            this.path = Paths.get(first, more);
            return this;
        }

        public Pom withTemplate(String template) {
            this.template = template;
            return this;
        }

        public Pom withTemplateParam(String key, Object val) {
            this.templateParams.put(key, val);
            return this;
        }

        public Pom withTemplateParams(Map<String, Object> params) {
            this.templateParams.putAll(params);
            return this;
        }

        public Pom withTransformation(PomTransformer.Transformation transformation) {
            this.transformations.add(transformation);
            return this;
        }

        public void build() throws IOException {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            if (!Files.exists(path) && this.template != null) {
                try (InputStream is = getClass().getResourceAsStream(this.template)) {
                    if (is == null) {
                        throw new IOException("Unable to find template " + this.template);
                    }

                    MustacheFactory mf = new DefaultMustacheFactory();
                    Writer writer = new StringWriter();

                    Mustache mustache = mf.compile(new InputStreamReader(is), this.template);
                    mustache.execute(writer, this.templateParams);

                    Files.write(path, writer.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            if (!transformations.isEmpty()) {
                new PomTransformer(
                        path,
                        StandardCharsets.UTF_8,
                        PomTransformer.SimpleElementWhitespace.AUTODETECT_PREFER_EMPTY)
                        .transform(this.transformations);
            }
        }
    }
}
