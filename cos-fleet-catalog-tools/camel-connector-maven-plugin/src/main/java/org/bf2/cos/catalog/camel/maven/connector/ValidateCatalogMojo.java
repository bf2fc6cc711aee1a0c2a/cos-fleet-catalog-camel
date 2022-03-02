package org.bf2.cos.catalog.camel.maven.connector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

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
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/connectors")
    private String outputPath;
    @Parameter
    private List<Connector> connectors;
    @Parameter
    private List<File> validators;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("skip validate-catalog");
            return;
        }
        if (validators == null) {
            getLog().info("skip validate-catalog as no validator script is not set");
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
                final Object schema = new JsonSlurper().parse(schemaFile);

                Binding binding = new Binding();
                binding.setProperty("log", getLog());
                binding.setProperty("connector", connector);
                binding.setProperty("connector_id", id);
                binding.setProperty("schema", schema);

                for (File validator : validators) {
                    if (!Files.exists(validator.toPath())) {
                        getLog().info("skip validate-catalog as validator script " + validator + " does not exists");
                        return;
                    }

                    new GroovyShell(cl, binding, cc).run(validator, new String[] {});
                }
            }
        } catch (AssertionError e) {
            throw new MojoFailureException(e);
        } catch (Exception e) {
            throw new MojoFailureException(e);
        }
    }
}