package org.bf2.cos.connector.camel.it.support.spock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.spockframework.runtime.extension.ExtensionAnnotation;
import org.spockframework.util.Beta;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtensionAnnotation(RequiresDefinitionExtension.class)
@Repeatable(RequiresDefinition.Container.class)
public @interface RequiresDefinition {
    String value();

    @Beta
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface Container {
        RequiresDefinition[] value();
    }
}
