package io.quarkiverse.flow.dsl.model;

import java.io.Serializable;

record Address(String street, int number) implements Serializable {
}
