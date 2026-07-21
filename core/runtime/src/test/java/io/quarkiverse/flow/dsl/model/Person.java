package io.quarkiverse.flow.dsl.model;

import java.io.Serializable;

record Person(String name, int age, Address address) implements Serializable {
}
