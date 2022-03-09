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

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("rc")
public class RouteControllerComponent extends DefaultComponent {

    private ExecutorService executorService;

    public RouteControllerComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RouteControllerEndpoint answer = new RouteControllerEndpoint(uri, this);
        answer.setAction(remaining);

        setProperties(answer, parameters);

        return answer;
    }

    synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "ControlBus");
        }
        return executorService;
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
        super.doStop();
    }
}