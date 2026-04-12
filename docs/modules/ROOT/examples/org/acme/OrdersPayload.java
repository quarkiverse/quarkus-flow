package org.acme;

import java.util.List;

public record OrdersPayload(List<Order> orders) {}

