package io.quarkiverse.flow.persistence.common.hashing;

import java.util.Optional;

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
        Optional<Integer> threshold();
    }

    interface SDK {
        /**
         * If the object byte representation is larger than the configured threshold, Java SDK trivial hashing will be used to
         * store
         * the byte[] in a separated place
         * If not, the byte[] will be written embedded
         */
        Optional<Integer> threshold();
    }
}
