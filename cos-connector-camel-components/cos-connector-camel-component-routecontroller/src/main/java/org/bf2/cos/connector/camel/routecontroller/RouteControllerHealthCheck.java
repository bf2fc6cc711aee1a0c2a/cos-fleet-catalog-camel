package org.bf2.cos.connector.camel.routecontroller;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.spi.annotations.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import javax.enterprise.inject.spi.CDI;
import java.util.Locale;
import java.util.Map;

@HealthCheck("rc-check")
public class RouteControllerHealthCheck extends AbstractHealthCheck {
    private static final String DATA_ROUTE_ID = "route.id";
    private static final String DATA_ROUTE_STATUS = "route.status";
    private static final String DATA_ERROR_MESSAGE = "error.message";

    public RouteControllerHealthCheck() {
        super("custom-camel-routecontroller-health-check");
    }

    @Override
    public boolean isReadiness() {
        return true;
    }

    @Override
    public boolean isLiveness() {
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        CamelContext context = getCamelContext();

        builder.unknown();
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
                        builder = builder.detail(DATA_ERROR_MESSAGE, cause.getMessage());
                    }
                }

                builder
                        .detail(DATA_ROUTE_ID, route.getId())
                        .detail(DATA_ROUTE_STATUS, status.name().toLowerCase(Locale.US))
                        .down();
            }
        }
        if (State.UNKNOWN.equals(builder.state())) {
            builder.up();
        }
    }

}