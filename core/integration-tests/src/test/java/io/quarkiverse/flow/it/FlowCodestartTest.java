package io.quarkiverse.flow.it;

import static io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog.Language.JAVA;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartData;
import io.quarkus.devtools.testing.codestarts.QuarkusCodestartTest;
import io.quarkus.maven.dependency.ArtifactKey;

public class FlowCodestartTest {

    @RegisterExtension
    public static QuarkusCodestartTest codestartTest = QuarkusCodestartTest.builder()
            .languages(JAVA)
            .setupStandaloneExtensionTest("io.quarkiverse.flow:quarkus-flow")
            .extension(ArtifactKey.fromString("io.quarkus:quarkus-rest-jackson"))
            .putData(QuarkusCodestartData.QuarkusDataKey.APP_CONFIG, Map.of("quarkus.http.test-port", "9999"))
            .build();

    @Test
    void testContent() throws Throwable {
        codestartTest.checkGeneratedSource("org.acme.HelloWorkflow");
        codestartTest.checkGeneratedSource("org.acme.HelloResource");
        codestartTest.checkGeneratedSource("org.acme.Message");
        codestartTest.checkGeneratedTestSource("org.acme.HelloResourceTest");
    }

    @Test
    void buildAllProjects() throws IOException {
        codestartTest.buildAllProjects();
    }

}
