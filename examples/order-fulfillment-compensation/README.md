# Order Fulfillment with Compensation (Quarkus Flow + Saga Pattern)

A complete example demonstrating the **Compensation/Saga Pattern** for distributed transactions using:

- **Quarkus** (hot-reload dev mode)
- **[Quarkus Flow](https://docs.quarkiverse.io/quarkus-flow/dev/)** (function-first fluent DSL for Serverless Workflow)
- **Try-Catch Error Handling** with automatic compensation/rollback
- **WebSocket** for real-time UI updates
- A **Single Page Application (SPA)** UI with live workflow visualization

> Run locally with hot reload and _no manual infra setup_. Perfect for learning how to handle failures and rollbacks in distributed workflows.

---

## 🚀 Quick Start

### Prerequisites

- Java 17+ and Maven

### 1) Run the app

```bash
# from this example directory
mvn quarkus:dev
```

Open: **http://localhost:8080**

> Quarkus dev mode gives you live reload for Java, resources, and the static UI.

---

## 🧭 What you'll see

The UI is a clean, responsive Single Page Application that visualizes the order fulfillment workflow in real-time. You can:

1. **Submit Orders**: Enter one of four test order IDs to see different failure scenarios
2. **Watch Live Progress**: See each step execute in real-time via WebSocket updates
3. **Observe Compensation**: When a step fails, watch the automatic rollback of previous operations

### Test Scenarios

The example includes three pre-configured test orders that demonstrate different failure points:

- **ORDER#001**: Fails at **Stock Reservation** (out of stock)
  - Compensation: Cancels stock reservation
  
- **ORDER#002**: Fails at **Payment Processing** (service unavailable)
  - Compensation: Cancels payment → Cancels stock reservation
  
- **ORDER#003**: Fails at **Shipping** (carrier unavailable)
  - Compensation: Cancels shipping → Cancels payment → Cancels stock reservation

- **ORDER#004**: Succeeds through all steps with no failures

Any other order ID will complete successfully through all three steps.

---

## 🧩 Architecture: The Saga Pattern

This example implements the **Saga Pattern** for managing distributed transactions. When a step fails, compensation functions automatically rollback all previously completed operations, ensuring data consistency.

```text
┌─────────────────────────────────────────────────────────────────┐
│                    Order Fulfillment Workflow                    │
│                                                                   │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐  │
│  │   Reserve    │─────▶│   Process    │─────▶│   Schedule   │  │
│  │    Stock     │      │   Payment    │      │   Shipping   │  │
│  └──────┬───────┘      └──────┬───────┘      └──────┬───────┘  │
│         │                     │                     │           │
│         │ ✗ Fails             │ ✗ Fails             │ ✗ Fails   │
│         ▼                     ▼                     ▼           │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐  │
│  │   Cancel     │      │   Cancel     │      │   Cancel     │  │
│  │ Reservation  │      │   Payment    │      │   Shipping   │  │
│  └──────────────┘      └──────┬───────┘      └──────┬───────┘  │
│                               │                     │           │
│                               ▼                     ▼           │
│                        ┌──────────────┐      ┌──────────────┐  │
│                        │   Cancel     │      │   Cancel     │  │
│                        │ Reservation  │      │   Payment    │  │
│                        └──────────────┘      └──────┬───────┘  │
│                                                     ▼           │
│                                              ┌──────────────┐  │
│                                              │   Cancel     │  │
│                                              │ Reservation  │  │
│                                              └──────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Concepts

1. **Forward Flow**: Each step (stock → payment → shipping) executes sequentially
2. **Compensation Flow**: On failure, compensation functions execute in reverse order
3. **Atomic Operations**: Each step is either fully completed or fully compensated
4. **Error Propagation**: Errors trigger immediate compensation and workflow termination

---

## 🔧 The Workflow Definition

The workflow uses Quarkus Flow's fluent DSL with nested `tryCatch` blocks. Each business operation has a corresponding compensation function:

```java
public Workflow descriptor() {
    return FuncWorkflowBuilder.workflow("order-fulfillment", "examples")
        .tasks(
            // Step 1: Try to reserve stock
            tryCatch("tryStockReservation",
                t -> t.tryCatch(function("stockReservation", this::reserveStock))
                      .catchError(
                          err -> err.type(STOCK_ORDER_ERROR),
                          function("cancelStockReservation", this::cancelReservation)
                              .then(FlowDirectiveEnum.END)
                      )),
            
            // Step 2: Try to process payment
            tryCatch("tryPaymentProcessing",
                t -> t.tryCatch(function("paymentProcessing", this::processPayment))
                      .catchWhen(
                          "${ .status == 503 }",
                          function("cancelPayment", this::cancelPayment)
                              .then(FlowDirectiveEnum.END))),
            
            // Step 3: Try to schedule shipping
            tryCatch("tryShipping",
                t -> t.tryCatch(function("scheduleShipping", this::scheduleShipping))
                      .catchType(
                          SHIPPING_ERROR,
                          function("cancelShipping", this::cancelShipping)
                              .then(FlowDirectiveEnum.END))),
            
            // Final step: Mark workflow complete
            function("endFlow", this::endFlow)
        )
        .build();
}
```

### Error Handling Strategies

The example demonstrates three different error-catching approaches:

1. **`catchError`**: Match by error type (e.g., `STOCK_ORDER_ERROR`)
2. **`catchWhen`**: Match by condition expression (e.g., `${ .status == 503 }`)
3. **`catchType`**: Match by error type constant (e.g., `SHIPPING_ERROR`)

---

## 🎯 Business Logic

Each operation is implemented as a simple Java method that can succeed or fail:

```java
private OrderStep reserveStock(String order) {
    log.info("Reserving stock for order: {}", order);
    broadcastStep(order, "stockReservation", "processing", "Reserving stock...");
    
    if (order.equals(ORDER_001)) {
        broadcastStep(order, "stockReservation", "failed", "Out of stock");
        throw new WorkflowException(WorkflowError.error(STOCK_ORDER_ERROR, 409).build());
    }
    
    broadcastStep(order, "stockReservation", "completed", "Stock reserved");
    return new OrderStep(order, "reserved");
}

private OrderStep cancelReservation(String order) {
    log.info("Cancelling reservation for order: {}", order);
    broadcastStep(order, "compensation", "processing", "Cancelling stock reservation...");
    broadcastStep(order, "compensation", "completed", "Stock reservation cancelled");
    return new OrderStep(order, "error");
}
```

### Compensation Chain

When a later step fails, its compensation function calls the compensation of previous steps:

```java
private OrderStep cancelShipping(OrderStep order) {
    log.info("Cancel shipping for order: {}", order);
    broadcastStep(order.orderId(), "compensation", "processing", 
                  "Cancelling shipping and previous operations...");
    
    // Cascade: Cancel shipping → Cancel payment → Cancel stock
    cancelPayment(order);
    
    broadcastStep(order.orderId(), "compensation", "completed", 
                  "All operations cancelled");
    return new OrderStep(order.orderId(), "error");
}
```

---

## 🌐 REST API

### Start Order Processing

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": "ORDER#001"}'
```

**Response:**
```
HTTP/1.1 202 Accepted
Location: /orders/ORDER#001
```

The workflow executes asynchronously. Use the WebSocket UI or check logs to monitor progress.

---

## 📡 Real-Time Updates

The example uses WebSocket to push workflow state changes to the browser in real-time:

```java
private void broadcastStep(String orderId, String step, String status, String message) {
    String json = String.format(
        "{\"orderId\":\"%s\",\"step\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}",
        orderId, step, status, message
    );
    webSocket.broadcast(json);
}
```

The UI connects to `ws://localhost:8080/ws/orders` and displays each step as it executes.

---

## 🛠 Troubleshooting

- **WebSocket not connecting**: Check browser console for errors. Ensure you're accessing via `http://localhost:8080` (not a different host/port).

- **Workflow not starting**: Check Quarkus logs for exceptions. Verify the order ID is being sent correctly in the POST request.

- **No compensation on failure**: Ensure you're using one of the test order IDs (ORDER#001, ORDER#002, ORDER#003). Other IDs will succeed.

- **Port already in use**: Change the port in `application.properties`: `quarkus.http.port=8081`

---

## 📚 Learn More

This example demonstrates core concepts for building resilient distributed systems:

- **Saga Pattern**: Coordinating distributed transactions without 2PC
- **Compensation Logic**: Undoing completed operations when later steps fail
- **Error Handling**: Different strategies for catching and handling errors
- **Real-Time Monitoring**: Using WebSocket for live workflow visualization

### Key Takeaways

1. **Explicit Compensation**: Each forward operation has a corresponding compensation function
2. **Cascading Rollback**: Later compensations call earlier ones to maintain consistency
3. **Type-Safe Workflows**: Pure Java records and enums ensure compile-time safety
4. **Declarative Error Handling**: Try-catch blocks in the workflow DSL, not scattered in business logic

---

## 🎓 Extend This Example

Ideas for learning and experimentation:

- Add a fourth step (e.g., "Send Confirmation Email") with its own compensation
- Implement retry logic before compensation (e.g., retry payment 3 times)
- Add a database to persist order state across restarts
- Create a dashboard showing all orders and their current status
- Implement partial compensation (e.g., partial refund instead of full cancellation)

---

## 📖 Documentation

- **Quarkus Flow**: [https://docs.quarkiverse.io/quarkus-flow/dev/](https://docs.quarkiverse.io/quarkus-flow/dev/)
- **CNCF Serverless Workflow**: [https://serverlessworkflow.io/](https://serverlessworkflow.io/)
- **Saga Pattern**: [https://microservices.io/patterns/data/saga.html](https://microservices.io/patterns/data/saga.html)

Have fun building resilient workflows with Quarkus Flow! 🎉