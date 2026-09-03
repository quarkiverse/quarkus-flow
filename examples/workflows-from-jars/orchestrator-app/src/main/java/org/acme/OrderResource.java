package org.acme;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkiverse.flow.Flow;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

@Path("/orders")
public class OrderResource {

    /**
     * Contributed by the payments-workflows JAR - not defined in this application.
     */
    @Inject
    @Identifier("payments:authorize")
    Flow authorizePayment;

    /**
     * Contributed by the shipping-workflows JAR - not defined in this application.
     */
    @Inject
    @Identifier("shipping:quote")
    Flow shippingQuote;

    /**
     * Also shipped by payments-workflows (5% standard policy), but this application
     * redefines it in src/main/resources/flow/discount.yaml (10% promotional policy).
     * The application definition always wins over a dependency-provided one.
     */
    @Inject
    @Identifier("payments:discount")
    Flow discount;

    @GET
    @Path("/authorize")
    public Uni<Map<String, Object>> authorize(@QueryParam("orderId") String orderId,
            @QueryParam("amount") double amount) {
        return run(authorizePayment, Map.of("orderId", orderId, "amount", amount));
    }

    @GET
    @Path("/shipping-quote")
    public Uni<Map<String, Object>> shippingQuote(@QueryParam("weightKg") double weightKg) {
        return run(shippingQuote, Map.of("weightKg", weightKg));
    }

    @GET
    @Path("/discount")
    public Uni<Map<String, Object>> applyDiscount(@QueryParam("amount") double amount) {
        return run(discount, Map.of("amount", amount));
    }

    private static Uni<Map<String, Object>> run(Flow flow, Map<String, Object> input) {
        return flow.startInstance(input)
                .onItem()
                .transform(wf -> wf.asMap().orElseThrow());
    }
}
