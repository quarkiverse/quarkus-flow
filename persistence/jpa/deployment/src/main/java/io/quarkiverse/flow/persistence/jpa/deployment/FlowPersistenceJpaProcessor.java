package io.quarkiverse.flow.persistence.jpa.deployment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import io.quarkiverse.flow.persistence.jpa.FlowPersistenceJpaConfig;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalPersistenceUnitBuildItem;

public class FlowPersistenceJpaProcessor {

    private static final String FEATURE = "flow-persistence-jpa";
    private static final String ENTITY_PACKAGE = "io.quarkiverse.flow.persistence.jpa";
    private static final List<DotName> JPA_MODEL_ANNOTATIONS = List.of(
            DotName.createSimple(Entity.class),
            DotName.createSimple(Embeddable.class),
            DotName.createSimple(MappedSuperclass.class),
            DotName.createSimple(Converter.class));

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Only contributes Flow's entities to the persistence unit named by
     * {@link FlowPersistenceJpaConfig#persistenceUnitName()} when a datasource with that exact name is
     * actually configured; otherwise does nothing, leaving them to Hibernate ORM's normal
     * classpath auto-discovery into the application's default persistence unit. This keeps
     * applications that never configured that datasource unaffected: contributing a persistence
     * unit whose datasource doesn't exist would otherwise hard-fail the build.
     */
    @BuildStep
    void contributeFlowEntitiesToPersistenceUnit(
            FlowPersistenceJpaConfig config,
            CombinedIndexBuildItem index,
            List<JdbcDataSourceBuildItem> jdbcDataSources,
            BuildProducer<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnit) {
        String persistenceUnitName = config.persistenceUnitName();
        boolean dataSourceConfigured = jdbcDataSources.stream()
                .anyMatch(ds -> persistenceUnitName.equals(ds.getName()));
        if (!dataSourceConfigured) {
            return;
        }

        Set<String> flowModelClassNames = new HashSet<>();
        for (DotName annotation : JPA_MODEL_ANNOTATIONS) {
            for (AnnotationInstance annotationInstance : index.getIndex().getAnnotations(annotation)) {
                DotName className = annotationInstance.target().asClass().name();
                if (ENTITY_PACKAGE.equals(className.packagePrefix())) {
                    flowModelClassNames.add(className.toString());
                }
            }
        }

        additionalPersistenceUnit.produce(AdditionalPersistenceUnitBuildItem.builder(persistenceUnitName)
                .dataSourceName(persistenceUnitName)
                .managedClasses(flowModelClassNames)
                // AdditionalPersistenceUnitBuildItem bypasses quarkus.hibernate-orm."name".* entirely, so the
                // naming/schema conventions Flow's entities are mapped against must be set directly as raw
                // Hibernate properties rather than relying on consumer-configured
                // quarkus.hibernate-orm.physical-naming-strategy schema-management.strategy,
                // neither of which apply to this persistence unit.
                .property("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .property("hibernate.hbm2ddl.auto", "validate")
                .build());
    }
}
