package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;

public interface MetadataSupport<K, E extends MetadataEntity> {

    K key();

    Collection<E> getMetadata();

}
