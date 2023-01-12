package org.bf2.cos.connector.camel.it.support.spock;

import java.nio.file.Files;
import java.nio.file.Path;

import org.opentest4j.TestAbortedException;
import org.spockframework.runtime.extension.IAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

public class RequiresDefinitionExtension implements IAnnotationDrivenExtension<RequiresDefinition> {
    @Override
    public void visitSpecAnnotation(RequiresDefinition annotation, SpecInfo spec) {
        if (spec.isSkipped()) {
            return;
        }

        eval(annotation);
    }

    @Override
    public void visitFeatureAnnotation(RequiresDefinition annotation, FeatureInfo feature) {
        if (feature.getSpec().isSkipped()) {
            return;
        }

        eval(annotation);
    }

    private void eval(RequiresDefinition annotation) {
        String root = System.getProperties().getProperty("cos.catalog.definition.root");
        if (root == null) {
            throw new TestAbortedException("Required definition not found: " + annotation.value());
        }

        Path path;

        if (!annotation.value().startsWith(root)) {
            path = Path.of(root, annotation.value());
        } else {
            path = Path.of(annotation.value());
        }

        if (!Files.exists(path)) {
            throw new TestAbortedException("Required definition not found: " + annotation.value());
        }
    }
}
