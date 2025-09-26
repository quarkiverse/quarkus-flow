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
package io.quarkiverse.flow.it;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkiverse.flow.FlowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Blocking;

@Path("/hello")
@ApplicationScoped
public class HelloAgenticResource {

    @Inject
    @FlowDefinition("helloAgenticWorkflow")
    WorkflowDefinition helloAgenticWorkflow;

    @POST
    @Blocking
    public CompletionStage<String> hello(String message) {
        return helloAgenticWorkflow
                .instance(message)
                .start()
                .thenApply(w -> w.asText().orElseThrow());
    }
}
