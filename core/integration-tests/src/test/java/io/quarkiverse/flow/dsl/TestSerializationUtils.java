package io.quarkiverse.flow.dsl;

import static io.serverlessworkflow.api.WorkflowReader.readWorkflow;
import static io.serverlessworkflow.api.WorkflowWriter.writeWorkflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.types.Workflow;

class TestSerializationUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestSerializationUtils.class);

    private TestSerializationUtils() {
    }

    static Workflow writeAndReadInMemory(Workflow workflow) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeWorkflow(out, workflow, WorkflowFormat.YAML);
            bytes = out.toByteArray();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Workflow string representation is {}", new String(bytes));
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return readWorkflow(in, WorkflowFormat.YAML);
        }
    }
}
