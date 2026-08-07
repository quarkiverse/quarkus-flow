package org.acme.bestpractices;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderService {

    // tag::transactional-service[]
    @Transactional
    public Long placeOrder(Order order) {
        order.persist();
        return order.id;
    }
    // end::transactional-service[]
}
