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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultRouteError;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.RouteError;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The control bus producer.
 */
public class RouteControllerProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RouteControllerProducer.class);

    private final CamelLogger logger;

    public RouteControllerProducer(Endpoint endpoint, CamelLogger logger) {
        super(endpoint);
        this.logger = logger;
    }

    @Override
    public RouteControllerEndpoint getEndpoint() {
        return (RouteControllerEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (getEndpoint().getAction() != null) {
            try {
                getEndpoint().getComponent().getExecutorService().submit(new ActionTask(exchange));
            } catch (Exception e) {
                exchange.setException(e);
            }
        }

        callback.done(true);
        return true;
    }

    /**
     * Tasks to run when processing by route action.
     */
    private final class ActionTask implements Runnable {

        private final Exchange exchange;

        private ActionTask(Exchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void run() {
            String action = getEndpoint().getAction();
            String id = getEndpoint().getRouteId();

            if (ObjectHelper.equal("current", id)) {
                id = ExchangeHelper.getRouteId(exchange);
            }

            String task = action + " route " + id;

            try {
                switch (action) {
                    case "fail":
                        Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                        LOG.error("Failing route: {}", id, e);

                        getEndpoint().getCamelContext().getRouteController().stopRoute(id);

                        DefaultRouteError.set(
                                exchange.getContext(),
                                id,
                                RouteError.Phase.STOP,
                                e,
                                true);

                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported action: " + action);
                }

                logger.log("EH task done [" + task + "]");
            } catch (Exception e) {
                logger.log("Error executing EH task [" + task + "]. This exception will be ignored.", e);
            }
        }

    }

}