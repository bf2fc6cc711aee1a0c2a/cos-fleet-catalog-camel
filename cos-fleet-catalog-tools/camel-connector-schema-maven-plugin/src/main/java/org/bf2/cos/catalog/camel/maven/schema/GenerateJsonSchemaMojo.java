package org.bf2.cos.catalog.camel.maven.schema;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.google.gson.annotations.SerializedName;

@Mojo(name = "generate-json-schema", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateJsonSchemaMojo extends AbstractMojo {
    @Parameter(defaultValue = "false", property = "cos.schema.skip")
    private boolean skip = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/schemas/json")
    private String outputPath;

    @Parameter
    private Set<String> classNames;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ClassLoader cl = computeClassLoader();
        ObjectMapper mapper = new ObjectMapper();

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                mapper,
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
        SchemaGenerator generator = new SchemaGenerator(config);

        if (classNames != null) {
            for (String className : classNames) {
                try {
                    Class<?> type = cl.loadClass(className);
                    JsonNode jsonSchema = generator.generateSchema(type);

                    Path out = Paths.get(outputPath);
                    Path file = out.resolve(type.getName() + ".json");

                    Files.createDirectories(out);

                    getLog().info("Writing schema to: " + file);

                    mapper.writerWithDefaultPrettyPrinter().writeValue(
                            Files.newBufferedWriter(file),
                            jsonSchema);
                } catch (Exception e) {
                    throw new MojoExecutionException(e);
                }
            }
        }
    }

    private ClassLoader computeClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); ++i) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, GenerateJsonSchemaMojo.class.getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }
}
