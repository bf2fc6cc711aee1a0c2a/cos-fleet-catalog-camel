package org.bf2.cos.catalog.camel.maven.connector.support;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.project.MavenProject;

import io.quarkus.maven.dependency.ResolvedDependency;

public final class AppBootstrapSupport {
    public static final String NATIVE_PROFILE_NAME = "native";

    private AppBootstrapSupport() {
    }

    public static boolean isNativeProfileEnabled(MavenProject mavenProject) {
        // gotcha: mavenProject.getActiveProfiles() does not always contain all active profiles (sic!),
        //         but getInjectedProfileIds() does (which has to be "flattened" first)
        final Collection<List<String>> profiles = mavenProject.getInjectedProfileIds().values();
        final Stream<String> activeProfileIds = profiles.stream().flatMap(List<String>::stream);

        if (activeProfileIds.anyMatch(NATIVE_PROFILE_NAME::equalsIgnoreCase)) {
            return true;
        }

        // recurse into parent (if available)
        return Optional.ofNullable(mavenProject.getParent())
                .map(AppBootstrapSupport::isNativeProfileEnabled)
                .orElse(false);
    }

    public static String digestAsHex(ResolvedDependency dependency) throws IOException {
        return new DigestUtils("SHA-256").digestAsHex(dependency.getResolvedPaths().getSinglePath());
    }
}
