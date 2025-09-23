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
package io.quarkiverse.quarkus.flow.it;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.ResponseStatus;

import io.quarkiverse.flow.FlowRunner;

@Path("/hello")
@ApplicationScoped
public class HelloResource {

    @Inject
    FlowRunner workflowRunner;

    @ResponseStatus(200)
    @GET
    public CompletionStage<Message> hello() {
        return workflowRunner
                .start("hello", Map.of()).thenApply(w -> w.as(Message.class).orElseThrow());
    }
}
