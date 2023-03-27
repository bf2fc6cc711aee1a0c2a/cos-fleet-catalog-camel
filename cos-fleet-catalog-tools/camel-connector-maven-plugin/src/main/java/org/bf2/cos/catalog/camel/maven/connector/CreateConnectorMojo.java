package org.bf2.cos.catalog.camel.maven.connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bf2.cos.catalog.camel.maven.connector.support.Builders;
import org.l2x6.pom.tuner.model.Gavtcs;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import static org.l2x6.pom.tuner.PomTransformer.Transformation.addManagedDependencyIfNeeded;
import static org.l2x6.pom.tuner.PomTransformer.Transformation.addModuleIfNeeded;

@Mojo(name = "create-connector")
public class CreateConnectorMojo extends AbstractMojo {
    private static final String PROPERTY_CONNECTOR_GROUP = "connector.group";
    private static final String PROPERTY_CONNECTOR_TYPE = "connector.type";
    private static final String PROPERTY_CONNECTOR_VERSION = "connector.version";
    private static final String PROPERTY_CONNECTOR_SOURCE = "connector.source";
    private static final String PROPERTY_CONNECTOR_SINK = "connector.sink";

    @Parameter(defaultValue = "false", property = "connector.generate.skip")
    private boolean skip = false;

    @Parameter(required = true, property = PROPERTY_CONNECTOR_GROUP)
    String connectorGroup;
    @Parameter(required = true, property = PROPERTY_CONNECTOR_TYPE)
    String connectorType;
    @Parameter(required = true, property = PROPERTY_CONNECTOR_VERSION)
    String connectorVersion;
    @Parameter(defaultValue = "true", property = PROPERTY_CONNECTOR_SOURCE)
    boolean connectorSource;
    @Parameter(defaultValue = "true", property = PROPERTY_CONNECTOR_SINK)
    boolean connectorSink;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping create-component");
            return;
        }

        final Map<String, Object> params = Map.of(
                PROPERTY_CONNECTOR_GROUP, connectorGroup,
                PROPERTY_CONNECTOR_TYPE, connectorType,
                PROPERTY_CONNECTOR_VERSION, connectorVersion,
                PROPERTY_CONNECTOR_SOURCE, connectorSource,
                PROPERTY_CONNECTOR_SINK, connectorSink);

        try {

            //
            // connector
            //

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-connectors", connectorGroup, connectorId(), "pom.xml")
                    .withTemplate("/create-connector-template/connector-pom.xml")
                    .withTemplateParams(params)
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-connectors", connectorGroup, "pom.xml")
                    .withTemplate("/create-connector-template/connector-group-pom.xml")
                    .withTemplateParams(params)
                    .withTransformation(
                            addModuleIfNeeded(connectorId(), String::compareTo))
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-connectors", "pom.xml")
                    .withTemplateParams(params)
                    .withTransformation(
                            addModuleIfNeeded(connectorGroup, String::compareTo))
                    .build();

            //
            // test
            //

            new Builders.Pom()
                    .withPath(testPath(), "pom.xml")
                    .withTemplate("/create-connector-template/connector-test-pom.xml")
                    .withTemplateParams(params)
                    .build();

            new Builders.Pom()
                    .withPath(testPath(), "src/main/resources/connector.txt")
                    .withTemplate("/create-connector-template/connector.txt")
                    .withTemplateParams(params)
                    .build();

            new Builders.Pom()
                    .withPath(testPath(), "src/test/resources/logback-test.xml")
                    .withTemplate("/create-connector-template/logback-test.xml")
                    .withTemplateParams(params)
                    .build();

            new Builders.Pom()
                    .withPath(testSourcePath(), "ConnectorContainerIT.groovy")
                    .withTemplate("/create-connector-template/ConnectorContainerIT.groovy")
                    .withTemplateParams(params)
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-connectors-it", connectorGroup, "pom.xml")
                    .withTemplate("/create-connector-template/connector-group-test-pom.xml")
                    .withTemplateParams(params)
                    .withTransformation(
                            addModuleIfNeeded(connectorType + "-it", String::compareTo))
                    .build();

            new Builders.Pom()
                    .withPath("cos-fleet-catalog-connectors-it", "pom.xml")
                    .withTemplateParams(params)
                    .withTransformation(
                            addModuleIfNeeded(connectorGroup, String::compareTo))
                    .build();

            //
            // main pom
            //

            new Builders.Pom()
                    .withPath("pom.xml")
                    .withTemplateParams(params)
                    .withTransformation(
                            addManagedDependencyIfNeeded(gav()))
                    .build();

            //
            // workflow
            //

            Path workflowPath = Paths.get(".github/workflows/build-pr.yaml");
            if (!Files.exists(workflowPath)) {
                throw new MojoExecutionException("Unable to find github workflow build-pr.yaml");
            }

            YAMLMapper mapper = new YAMLMapper();
            mapper.configure(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true);
            mapper.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);

            ObjectNode workflow = mapper.readValue(workflowPath.toFile(), ObjectNode.class);
            ObjectNode job = workflow.with("jobs").with(connectorGroup);
            job.putArray("needs").add("build");
            job.put("uses", "./.github/workflows/build-it.yaml");
            job.put("secrets", "inherit");
            job.putObject("with")
                    .put("modules", testModules())
                    .put("tag", "${{ github.run_id }}-${{ github.run_attempt }}");

            Files.writeString(
                    workflowPath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow));
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private String testModules() {
        return String.format(
                ":cos-fleet-catalog-connectors-%s,:cos-fleet-catalog-connectors-%s-it",
                connectorGroup,
                connectorGroup);
    }

    private String connectorId() {
        return connectorType + "-" + connectorVersion;
    }

    private String connectorTestId() {
        return connectorType + "-it";
    }

    private Gavtcs gav() {
        return new Gavtcs("cos.bf2", "cos-connector-" + connectorType + "-" + connectorVersion, "0.0.1-SNAPSHOT");
    }

    private String testPackage() {
        return "org.bf2.cos.connector.camel.it." + connectorGroup + "." + connectorType;
    }

    private String testPath() {
        return String.format(
                "cos-fleet-catalog-connectors-it/%s/%s",
                connectorGroup,
                connectorTestId());
    }

    private String testSourcePath() {
        return String.format(
                "%s/src/test/groovy/%s",
                testPath(),
                testPackage().replace(".", "/"));
    }
}
