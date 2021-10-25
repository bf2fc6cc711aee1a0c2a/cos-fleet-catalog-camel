package org.bf2.cos.catalog.camel.maven.connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.google.gson.annotations.SerializedName;

@Mojo(name = "generate-json-schema", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateJsonSchemaMojo extends AbstractConnectorMojo {
    @Parameter(defaultValue = "false", property = "cos.schema.skip")
    private boolean skip = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/schemas/json")
    private String outputPath;

    private final SchemaGenerator generator;

    public GenerateJsonSchemaMojo() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                CatalogSupport.JSON_MAPPER,
                SchemaVersion.DRAFT_2019_09,
                OptionPreset.PLAIN_JSON);

        configBuilder.forFields()
                .withPropertyNameOverrideResolver(field -> {
                    SerializedName sn = field.getAnnotationConsideringFieldAndGetter(SerializedName.class);
                    if (sn != null) {
                        return sn.value();
                    }

                    return null;
                });

        SchemaGeneratorConfig config = configBuilder.build();

        this.generator = new SchemaGenerator(config);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        final ClassLoader cl = CatalogSupport.getClassLoader(project);

        for (Connector connector : getConnectors()) {
            if (connector.getDataShape() == null) {
                continue;
            }

            try {
                if (connector.getDataShape().getConsumes() != null) {
                    generateSchema(cl, connector.getDataShape().getConsumes());
                }
                if (connector.getDataShape().getProduces() != null) {
                    generateSchema(cl, connector.getDataShape().getProduces());
                }
            } catch (Exception e) {
                throw new MojoFailureException(e);
            }
        }
    }

    private void generateSchema(ClassLoader cl, Connector.DataShape dataShape) throws Exception {
        if (dataShape.getContentClass() == null) {
            return;
        }

        Class<?> type = cl.loadClass(dataShape.getContentClass());
        JsonNode jsonSchema = generator.generateSchema(type);

        Path out = Paths.get(outputPath);
        Path file = out.resolve(type.getName() + ".json");

        Files.createDirectories(out);

        getLog().info("Writing schema to: " + file);

        CatalogSupport.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(
                Files.newBufferedWriter(file),
                jsonSchema);
    }
}
