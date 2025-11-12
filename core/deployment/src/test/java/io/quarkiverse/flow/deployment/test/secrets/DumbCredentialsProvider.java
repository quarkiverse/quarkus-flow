package io.quarkiverse.flow.deployment.test.secrets;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.quarkus.credentials.CredentialsProvider;

@ApplicationScoped
@Named("dumb")
public class DumbCredentialsProvider implements CredentialsProvider {

    @Override
    public Map<String, String> getCredentials(String credentialsName) {
        if ("mySecret".equals(credentialsName)) {
            return Map.of(
                    "password", "s3cr3t!");
        }
        return Map.of();
    }
}
