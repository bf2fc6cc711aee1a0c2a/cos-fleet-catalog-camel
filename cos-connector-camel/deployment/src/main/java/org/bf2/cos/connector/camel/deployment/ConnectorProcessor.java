package org.bf2.cos.connector.camel.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.health.HealthCheckRegistryRepository;
import org.apache.camel.quarkus.core.deployment.main.spi.CamelMainListenerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelBeanBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextCustomizerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceDestination;
import org.apache.camel.quarkus.core.deployment.spi.CamelServicePatternBuildItem;
import org.apache.camel.quarkus.core.deployment.util.CamelSupport;
import org.apache.camel.util.ObjectHelper;
import org.bf2.cos.connector.camel.ConnectorConfig;
import org.bf2.cos.connector.camel.ConnectorRecorder;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

public class ConnectorProcessor {

    private static final DotName CAMEL_HEALTH_CHECK_DOTNAME = DotName.createSimple(HealthCheck.class.getName());

    private static final DotName CAMEL_HEALTH_CHECK_REPOSITORY_DOTNAME = DotName
            .createSimple(HealthCheckRepository.class.getName());

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelContextCustomizerBuildItem customizeContext(ConnectorRecorder recorder, ConnectorConfig config) {
        return new CamelContextCustomizerBuildItem(recorder.createContextCustomizer(config));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelMainListenerBuildItem customizeMain(ConnectorRecorder recorder, ConnectorConfig config) {
        return new CamelMainListenerBuildItem(recorder.createMainCustomizer(config));
    }

    @BuildStep
    CamelServicePatternBuildItem excludeDefaultHealthCheckRegistry() {
        // Prevent camel main from overwriting the HealthCheckRegistry configured by this extension
        return new CamelServicePatternBuildItem(CamelServiceDestination.DISCOVERY, false,
                "META-INF/services/org/apache/camel/" + HealthCheckRegistry.FACTORY);
    }

    @BuildStep
    List<CamelBeanBuildItem> camelHealthDiscovery(CombinedIndexBuildItem combinedIndex) {
        IndexView index = combinedIndex.getIndex();
        List<CamelBeanBuildItem> buildItems = new ArrayList<>();

        Collection<ClassInfo> checks = index.getAllKnownImplementors(CAMEL_HEALTH_CHECK_DOTNAME);
        Collection<ClassInfo> repos = index.getAllKnownImplementors(CAMEL_HEALTH_CHECK_REPOSITORY_DOTNAME);

        // Create CamelBeanBuildItem to bind instances of HealthCheck to the camel registry
        checks.stream()
                .filter(CamelSupport::isConcrete)
                .filter(CamelSupport::isPublic)
                .filter(ClassInfo::hasNoArgsConstructor)
                .filter(ci -> !ci.name().toString().equals(HealthCheckRegistryRepository.class.getName()))
                .map(this::createHealthCamelBeanBuildItem)
                .forEach(buildItems::add);

        // Create CamelBeanBuildItem to bind instances of HealthCheckRepository to the camel registry
        repos.stream()
                .filter(CamelSupport::isConcrete)
                .filter(CamelSupport::isPublic)
                .filter(ClassInfo::hasNoArgsConstructor)
                .filter(ci -> !ci.name().toString().equals(HealthCheckRegistryRepository.class.getName()))
                .map(this::createHealthCamelBeanBuildItem)
                .forEach(buildItems::add);

        return buildItems;
    }

    private CamelBeanBuildItem createHealthCamelBeanBuildItem(ClassInfo classInfo) {
        String beanName;
        String className = classInfo.name().toString();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Class<?> clazz = classLoader.loadClass(className);
            Object health = clazz.getDeclaredConstructor().newInstance();
            if (health instanceof HealthCheck) {
                beanName = ((HealthCheck) health).getId();
            } else if (health instanceof HealthCheckRepository) {
                beanName = ((HealthCheckRepository) health).getId();
            } else {
                throw new IllegalArgumentException("Unknown health type " + className);
            }

            if (ObjectHelper.isEmpty(beanName)) {
                beanName = className;
            }

            return new CamelBeanBuildItem(beanName, className);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
