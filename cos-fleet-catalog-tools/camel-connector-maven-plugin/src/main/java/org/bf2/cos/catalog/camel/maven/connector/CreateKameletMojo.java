package org.bf2.cos.catalog.camel.maven.connector;

import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.Builders;
import org.l2x6.pom.tuner.model.Gavtcs;

import static org.l2x6.pom.tuner.PomTransformer.Transformation.addManagedDependencyIfNeeded;
import static org.l2x6.pom.tuner.PomTransformer.Transformation.addModuleIfNeeded;

@Mojo(name = "create-kamelet")
public class CreateKameletMojo extends AbstractMojo {
    private static final String PROPERTY_KAMELET_GROUP = "kamelet.group";
    private static final String PROPERTY_KAMELET_TYPE = "kamelet.type";
    private static final String PROPERTY_KAMELET_SOURCE = "kamelet.source";
    private static final String PROPERTY_KAMELET_SINK = "kamelet.sink";
    private static final String KAMELELT_PATH = "src/main/resources/kamelets";

    @Parameter(defaultValue = "false", property = "kamelet.generate.skip")
    private boolean skip = false;

    @Parameter(required = true, property = PROPERTY_KAMELET_GROUP)
    String kameletGroup;
    @Parameter(required = true, property = PROPERTY_KAMELET_TYPE)
    String kameletType;
    @Parameter(defaultValue = "true", property = PROPERTY_KAMELET_SOURCE)
    boolean kameletSource;
    @Parameter(defaultValue = "true", property = PROPERTY_KAMELET_SINK)
    boolean kameletSink;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping create-component");
            return;
        }

        try {
            if (this.kameletSource) {
                new Builders.Resource()
                        .withPath("cos-fleet-catalog-kamelets", kameletGroup, kameletType, KAMELELT_PATH,
                                kameletId() + "-source.kamelet.yaml")
                        .withTemplate("/create-kamelet-template/kamelet-source.yaml")
                        .withTemplateParam(PROPERTY_KAMELET_GROUP, kameletGroup)
                        .withTemplateParam(PROPERTY_KAMELET_TYPE, kameletType)
                        .build();
            }

            if (this.kameletSink) {
                new Builders.Resource()
                        .withPath("cos-fleet-catalog-kamelets", kameletGroup, kameletType, KAMELELT_PATH,
                                kameletId() + "-sink.kamelet.yaml")
                        .withTemplate("/create-kamelet-template/kamelet-sink.yaml")
                        .withTemplateParam(PROPERTY_KAMELET_GROUP, kameletGroup)
                        .withTemplateParam(PROPERTY_KAMELET_TYPE, kameletType)
                        .build();
            }

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-kamelets", kameletGroup, kameletType, "pom.xml")
                    .withTemplate("/create-kamelet-template/kamelet-pom.xml")
                    .withTemplateParam(PROPERTY_KAMELET_GROUP, kameletGroup)
                    .withTemplateParam(PROPERTY_KAMELET_TYPE, kameletType)
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-kamelets", kameletGroup, "pom.xml")
                    .withTransformation(
                            addModuleIfNeeded(kameletType, String::compareTo))
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-kamelets", "pom.xml")
                    .withTransformation(
                            addModuleIfNeeded(kameletGroup, String::compareTo))
                    .build();

            new Builders.Pom()
                    .withPath("pom.xml")
                    .withTransformation(
                            addManagedDependencyIfNeeded(gav()))
                    .build();
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private Gavtcs gav() {
        return new Gavtcs("cos.bf2", "cos-connector-kamelets-" + kameletType, "0.0.1-SNAPSHOT");
    }

    private String kameletId() {
        return "cos-" + kameletType;
    }
}
