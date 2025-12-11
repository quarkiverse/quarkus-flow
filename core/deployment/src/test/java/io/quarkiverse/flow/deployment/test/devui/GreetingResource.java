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
package io.quarkiverse.flow.deployment.test.devui;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.smallrye.mutiny.Uni;

@Path("/hello")
@ApplicationScoped
public class GreetingResource {

    @Inject
    DevUIWorkflow devUIWorkflow;

    @GET
    @ResponseStatus(200)
    public Uni<Message> hello() {
        return devUIWorkflow.startInstance(Map.of("name", "Quarkiverse")).onItem()
                .transform(wf -> wf.as(Message.class).orElseThrow());
    }
}
