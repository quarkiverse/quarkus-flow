package io.quarkiverse.flow.persistence.mvstore.test.recovery;

import java.util.Map;

final class RecoveryFunctions {
    private RecoveryFunctions() {
    }

    static Map<String, Object> passThrough(Map<String, Object> input) {
        return input;
    }
}
