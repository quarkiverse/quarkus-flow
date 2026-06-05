package io.quarkiverse.flow.persistence.jpa.test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Alert {

    public Alert(String tags) {
        this.tags = tags;
    }

    @Id
    @GeneratedValue
    private String id;
    private String tags;

    public String getTags() {
        return tags;
    }
}
