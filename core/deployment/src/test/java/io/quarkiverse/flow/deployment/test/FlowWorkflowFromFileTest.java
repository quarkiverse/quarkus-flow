package io.quarkiverse.flow.deployment.test;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class FlowWorkflowFromFileTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withConfigurationResource("application-flow-file.properties")
            .withApplicationRoot((jar) -> jar.addAsResource(new StringAsset("""
                    document:
                        dsl: '1.0.0'
                        namespace: default
                        name: call-http
                        version: '1.0.0'
                    do:
                      - wait30Seconds:
                          wait:
                            seconds: 30
                    """), "flow/duplicate.yaml")
                    .addAsResource(new StringAsset("""
                            document:
                              dsl: '1.0.0'
                              namespace: default
                              name: call-http
                              version: '1.0.0'
                            do:
                            - getPet:
                                call: http
                                with:
                                  method: get
                                  endpoint: https://petstore.swagger.io/v2/pet/{petId}
                            """), "flow/call-http.yaml"));

    @Test
    void shouldWarnDuplicateWorkflows() {
        unitTest.assertLogRecords(logs -> logs.stream()
                .anyMatch(logRecord -> logRecord.getMessage()
                        .contains("Duplicate workflow detected: namespace='default', name='call-http'")));
    }
}
