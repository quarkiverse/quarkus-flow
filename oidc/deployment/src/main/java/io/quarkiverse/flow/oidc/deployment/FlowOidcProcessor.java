package io.quarkiverse.flow.oidc.deployment;

import io.quarkiverse.flow.oidc.AuthenticationRegistry;
import io.quarkiverse.flow.oidc.QuarkusContextPropagator;
import io.quarkiverse.flow.oidc.SubjectTokenExtractor;
import io.quarkiverse.flow.oidc.cache.InMemoryTokenCacheRepository;
import io.quarkiverse.flow.oidc.cache.TokenRefreshMonitor;
import io.quarkiverse.flow.oidc.client.OidcClientProvider;
import io.quarkiverse.flow.oidc.client.TokenExchangeClient;
import io.quarkiverse.flow.oidc.config.AuthConfigResolver;
import io.quarkiverse.flow.oidc.lifecycle.TokenCleanupListener;
import io.quarkiverse.flow.oidc.providers.CachedTokenSource;
import io.quarkiverse.flow.oidc.providers.ClientCredentialsProvider;
import io.quarkiverse.flow.oidc.providers.TokenExchangeProvider;
import io.quarkiverse.flow.oidc.providers.TokenPropagationProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.serverlessworkflow.impl.executors.http.HttpRequestDecorator;

public class FlowOidcProcessor {

    private static final String FEATURE = "flow-oidc";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(
                        AuthenticationRegistry.class,
                        AuthConfigResolver.class,
                        SubjectTokenExtractor.class,
                        QuarkusContextPropagator.class,
                        OidcClientProvider.class,
                        TokenExchangeClient.class,
                        InMemoryTokenCacheRepository.class,
                        TokenRefreshMonitor.class,
                        TokenPropagationProvider.class,
                        ClientCredentialsProvider.class,
                        TokenExchangeProvider.class,
                        CachedTokenSource.class,
                        TokenCleanupListener.class)
                .build();
    }

    @BuildStep
    void registerDecoratorForNative(BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        serviceProviders.produce(
                ServiceProviderBuildItem.allProvidersFromClassPath(HttpRequestDecorator.class.getName()));
    }
}
