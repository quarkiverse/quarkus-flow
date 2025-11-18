package io.quarkiverse.flow.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;

public class FlowCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(QuarkusCodestartCatalog.Language.JAVA)
            .setupStandaloneExtensionTest("io.quarkiverse.flow:quarkus-flow")
            .build();

    @Test
    void testContent() throws Throwable {

        codestartTest.checkGeneratedSource("org.acme.HelloWorkflow");
        codestartTest.checkGeneratedSource("org.acme.HelloResource");
        codestartTest.checkGeneratedSource("org.acme.Message");
        codestartTest.checkGeneratedTestSource("org.acme.HelloResourceTest");

    }

}
