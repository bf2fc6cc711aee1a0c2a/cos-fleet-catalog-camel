/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bf2.cos.connector.camel.routecontroller;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "3.14.0", scheme = "rc", title = "Route Controller", syntax = "rc:action", producerOnly = true, category = {
        Category.CORE, Category.MONITORING })
public class RouteControllerEndpoint extends DefaultEndpoint {

    @UriPath(enums = "fail", defaultValue = "fail")
    @Metadata(required = true)
    private String action = "fail";

    @UriParam(defaultValue = "INFO")
    @Metadata(required = true)
    private String routeId;

    @UriParam(defaultValue = "INFO")
    private LoggingLevel loggingLevel = LoggingLevel.INFO;

    private transient CamelLogger logger;

    public RouteControllerEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    protected void doInit() throws Exception {
        this.logger = new CamelLogger(RouteControllerProducer.class.getName(), loggingLevel);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RouteControllerProducer(this, logger);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from a ControlBusEndpoint: " + getEndpointUri());
    }

    @Override
    public RouteControllerComponent getComponent() {
        return (RouteControllerComponent) super.getComponent();
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * The route id.
     */
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAction() {
        return action;
    }

    /**
     * The action.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * The logging level.
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}