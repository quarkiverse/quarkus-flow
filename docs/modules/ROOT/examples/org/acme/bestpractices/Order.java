package org.acme.bestpractices;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

public class Order extends PanacheEntity {

    public String product;
    public int quantity;

    public Order(String product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
}
