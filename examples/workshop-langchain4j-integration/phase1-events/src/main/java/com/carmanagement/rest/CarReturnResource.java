package com.carmanagement.rest;

import com.carmanagement.model.CarReturnEvent;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * REST endpoint for manually triggering car return events.
 * Useful for testing the event-driven Flow without external Kafka producers.
 *
 * Note: carNumber path parameter is used as the Kafka record key for partitioning.
 * The CarReturnEvent payload also contains carNumber field.
 */
@Path("/api/cars")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CarReturnResource {

    private static final Logger LOG = Logger.getLogger(CarReturnResource.class);

    @Inject
    @Channel("car-returns")
    Emitter<Record<Integer, CarReturnEvent>> carReturnEmitter;

    @POST
    @Path("/return/{carNumber}")
    public Response returnCar(@PathParam("carNumber") Integer carNumber, CarReturnEvent event) {
        if (event == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body is required"))
                    .build();
        }

        LOG.infof("Received car return request: car %d", carNumber);

        // Emit to Kafka topic (will trigger the Flow)
        carReturnEmitter.send(Record.of(carNumber, event));

        LOG.infof("Car return event emitted for car %d", carNumber);
        return Response.accepted()
                .entity(Map.of(
                        "message", "Car return event submitted",
                        "carNumber", carNumber))
                .build();
    }
}
