package org.acme.http.api;

import org.acme.http.workflows.oauth2.ClientCredentialsFlow;
import org.acme.http.workflows.oauth2.MultipleOAuth2ClientsFlow;
import org.acme.http.workflows.oauth2.OidcClientFlow;
import org.acme.http.workflows.oauth2.OpenAPIWithOAuth2Flow;
import org.acme.http.workflows.oauth2.PasswordGrantTypeFlow;
import org.acme.http.workflows.oauth2.TokenExchangeGrantTypeFlow;

import io.serverlessworkflow.impl.WorkflowModel;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/quarkus-flow")
public class FlowResource {

    @Inject
    ClientCredentialsFlow clientCredentials;

    @Inject
    MultipleOAuth2ClientsFlow multipleOAuth2Client;

    @Inject
    OpenAPIWithOAuth2Flow openAPIWithOAuth2;

    @Inject
    OidcClientFlow oidcClient;

    @Inject
    TokenExchangeGrantTypeFlow tokenExchangeGrantType;

    @Inject
    PasswordGrantTypeFlow passwordGrantType;

    @GET
    @Path("/oidc/images")
    public Response oidcListImages() {
        WorkflowModel model = oidcClient.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/token-exchange/images")
    public Response tokenExchangeListImages() {
        WorkflowModel model = tokenExchangeGrantType.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/password/images")
    public Response passwordListImages() {
        WorkflowModel model = passwordGrantType.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/images")
    public Response listMyImages() {
        WorkflowModel model = clientCredentials.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/openapi/images")
    public Response listMyImageUsingOpenAPI() {
        WorkflowModel model = openAPIWithOAuth2.instance().start().join();
        return Response.ok(model.asJavaObject()).build();
    }

    @GET
    @Path("/read-all-emails")
    public Response readAllEmails() {
        WorkflowModel model = multipleOAuth2Client.instance().start().join();
        return Response.ok(model).build();
    }
}
