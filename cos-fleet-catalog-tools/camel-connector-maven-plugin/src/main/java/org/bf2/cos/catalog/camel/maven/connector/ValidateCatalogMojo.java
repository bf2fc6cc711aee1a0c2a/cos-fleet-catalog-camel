package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ServiceLoader;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.bf2.cos.catalog.camel.maven.connector.validator.Validator;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import com.fasterxml.jackson.databind.node.ObjectNode;

import groovy.json.JsonSlurper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import static java.util.Optional.ofNullable;

@Mojo(name = "validate-catalog", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ValidateCatalogMojo extends AbstractMojo {

    @Parameter(defaultValue = "false", property = "cos.catalog.skip")
    private boolean skip = false;
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    @Parameter(defaultValue = "${project.build.outputDirectory}/connectors")
    private String outputPath;
    @Parameter
    private List<Connector> connectors;
    @Parameter
    private List<File> validators;
    @Parameter(defaultValue = "FAIL", property = "cos.catalog.validation.mode")
    private Validator.Mode mode;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("skip validate-catalog");
            return;
        }

        try {
            ImportCustomizer ic = new ImportCustomizer();

            CompilerConfiguration cc = new CompilerConfiguration();
            cc.addCompilationCustomizers(ic);

            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            for (Connector connector : connectors) {
                final String name = ofNullable(connector.getName()).orElseGet(project::getArtifactId);
                final String id = name.replace("-", "_");
                final Path schemaFile = Paths.get(outputPath).resolve(id + ".json");
                final ObjectNode on = CatalogSupport.JSON_MAPPER.readValue(schemaFile.toFile(), ObjectNode.class);
                final Validator.Context context = of(connector);

                for (Validator validator : ServiceLoader.load(Validator.class)) {
                    getLog().info("Validating: " + schemaFile + " with validator " + validator);
                    validator.validate(context, on);
                }

                if (validators != null) {
                    final Object schema = new JsonSlurper().parse(schemaFile);

                    Binding binding = new Binding();
                    binding.setProperty("context", context);
                    binding.setProperty("schema", schema);

                    for (File validator : validators) {
                        if (!Files.exists(validator.toPath())) {
                            return;
                        }

                        getLog().info("Validating: " + schemaFile + " with validator " + validator);
                        new GroovyShell(cl, binding, cc).run(validator, new String[] {});
                    }
                }
            }
        } catch (AssertionError | Exception e) {
            throw new MojoFailureException(e);
        }
    }

    private Validator.Context of(Connector connector) {
        return new Validator.Context() {
            @Override
            public Connector getConnector() {
                return connector;
            }

            @Override
            public Log getLog() {
                return ValidateCatalogMojo.this.getLog();
            }

            @Override
            public Validator.Mode getMode() {
                return mode;
            }
        };
    }
}