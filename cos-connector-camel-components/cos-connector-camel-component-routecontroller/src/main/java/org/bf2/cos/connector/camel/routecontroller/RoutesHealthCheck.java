package org.bf2.cos.connector.camel.routecontroller;

import java.util.Locale;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class RoutesHealthCheck implements HealthCheck {
    private static final String NAME = "custom-camel-routes-health-check";
    private static final String DATA_ROUTE_ID = "route.id";
    private static final String DATA_ROUTE_STATUS = "route.status";
    private static final String DATA_ERROR_MESSAGE = "error.message";

    @Inject
    CamelContext context;

    @Override
    public HealthCheckResponse call() {
        var builder = HealthCheckResponse.named(NAME);

        for (Route route : context.getRoutes()) {
            final ServiceStatus status = context.getRouteController().getRouteStatus(route.getId());
            if (status.isStopped()) {
                var error = route.getLastError();

                if (error != null && error.getException() != null) {
                    Throwable cause = error.getException();

                    for (error.getException(); cause != null;) {
                        if (cause.getMessage() != null) {
                            break;
                        }

                        cause = cause.getCause();
                    }

                    if (cause != null && cause.getMessage() != null) {
                        builder = builder.withData(DATA_ERROR_MESSAGE, cause.getMessage());
                    }
                }

                return builder
                        .withData(DATA_ROUTE_ID, route.getId())
                        .withData(DATA_ROUTE_STATUS, status.name().toLowerCase(Locale.US))
                        .down()
                        .build();
            }
        }

        return builder.up().build();
    }
}