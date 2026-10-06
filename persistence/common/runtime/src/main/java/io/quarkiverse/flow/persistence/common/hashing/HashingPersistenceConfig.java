package io.quarkiverse.flow.persistence.common.hashing;

import java.util.OptionalInt;

import io.smallrye.config.WithDefault;

public interface HashingPersistenceConfig {

    /** MD5 hashing related configuration */
    MD5 md5();

    /** Java SDK hashing related configuration */
    SDK sdk();

    interface MD5 {
        /**
         * If the object byte representation is larger than the configured threshold, MD5 hashing will be used to store the
         * byte[] in a separated place
         * If not, the byte[] will be written embedded
         */
        OptionalInt threshold();

        /** If this hashing strategy should be enable or not, default true */
        @WithDefault("true")
        boolean enabled();
    }

    interface SDK {
        /**
         * If the object byte representation is larger than the configured threshold, Java SDK trivial hashing will be used to
         * store
         * the byte[] in a separated place
         * If not, the byte[] will be written embedded
         */
        OptionalInt threshold();

        /** If this hashing strategy should be enable or not, default true */
        @WithDefault("false")
        boolean enabled();
    }
}
