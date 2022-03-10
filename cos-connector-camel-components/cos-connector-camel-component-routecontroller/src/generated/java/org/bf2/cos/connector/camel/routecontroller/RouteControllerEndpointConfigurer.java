/* Generated by camel build tools - do NOT edit this file! */
package org.bf2.cos.connector.camel.routecontroller;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.ConfigurerStrategy;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.support.component.PropertyConfigurerSupport;

/**
 * Generated by camel build tools - do NOT edit this file!
 */
@SuppressWarnings("unchecked")
public class RouteControllerEndpointConfigurer extends PropertyConfigurerSupport implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

    @Override
    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {
        RouteControllerEndpoint target = (RouteControllerEndpoint) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "lazystartproducer":
        case "lazyStartProducer": target.setLazyStartProducer(property(camelContext, boolean.class, value)); return true;
        case "logginglevel":
        case "loggingLevel": target.setLoggingLevel(property(camelContext, org.apache.camel.LoggingLevel.class, value)); return true;
        case "routeid":
        case "routeId": target.setRouteId(property(camelContext, java.lang.String.class, value)); return true;
        default: return false;
        }
    }

    @Override
    public Class<?> getOptionType(String name, boolean ignoreCase) {
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "lazystartproducer":
        case "lazyStartProducer": return boolean.class;
        case "logginglevel":
        case "loggingLevel": return org.apache.camel.LoggingLevel.class;
        case "routeid":
        case "routeId": return java.lang.String.class;
        default: return null;
        }
    }

    @Override
    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {
        RouteControllerEndpoint target = (RouteControllerEndpoint) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "lazystartproducer":
        case "lazyStartProducer": return target.isLazyStartProducer();
        case "logginglevel":
        case "loggingLevel": return target.getLoggingLevel();
        case "routeid":
        case "routeId": return target.getRouteId();
        default: return null;
        }
    }
}
