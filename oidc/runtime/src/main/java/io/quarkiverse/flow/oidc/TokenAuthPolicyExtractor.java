package io.quarkiverse.flow.oidc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.expressions.ExpressionUtils;

/**
 * Extracts OAuth2 and OIDC authentication policies from workflow definitions.
 * <p>
 * Scans named policies ({@code workflow.use.authentications}) and inline task-level authentication
 * across all task types (HTTP, gRPC, A2A, OpenAPI) and control flow structures (fork, for, try, do).
 */
public final class TokenAuthPolicyExtractor {

    private TokenAuthPolicyExtractor() {
    }

    /**
     * Extracts all static (non-expression-based) token authentication policies from a workflow.
     * <p>
     * Static policies have all configuration values as literals (no runtime expressions like {@code ${ $secret.xxx }}).
     * These policies can be registered eagerly at build-time or application startup.
     * <p>
     * Policies containing expressions in any field (authority, credentials, scopes, etc.) are skipped
     * and must be resolved and registered lazily at workflow execution time when the runtime context
     * is available for expression evaluation.
     *
     * @param workflow the workflow to scan for authentication policies
     * @return list of static OAuth2/OIDC policies found in named policies and task-level inline auth
     */
    public static List<TokenAuthPolicy> extractStaticTokenAuthPolicies(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        final List<TokenAuthPolicy> tokenAuthPolicies = new ArrayList<>();

        if (workflow.getUse() != null && workflow.getUse().getAuthentications() != null) {
            workflow
                    .getUse()
                    .getAuthentications()
                    .getAdditionalProperties()
                    .forEach((key, value) -> TokenAuthPolicy.from(key, value)
                            .ifPresent(t -> {
                                if (!containsExpressions(t))
                                    tokenAuthPolicies.add(t);
                            }));
        }

        extractTokenAuthPoliciesInto(workflow, workflow.getDo(), tokenAuthPolicies);

        return tokenAuthPolicies;
    }

    private static boolean containsExpressions(TokenAuthPolicy policy) {
        return containsExpressions(policy.commonAuth());
    }

    private static boolean containsExpressions(OAuth2AuthenticationData data) {
        if (data == null)
            return false;

        if (data.getAuthority() != null) {
            String authority = data.getAuthority().getLiteralUriTemplate();
            if (ExpressionUtils.isExpr(authority)) {
                return true;
            }
        }

        if (data.getClient() != null) {
            if (ExpressionUtils.isExpr(data.getClient().getId()))
                return true;
            if (ExpressionUtils.isExpr(data.getClient().getSecret()))
                return true;
        }

        if (ExpressionUtils.isExpr(data.getUsername()))
            return true;
        return ExpressionUtils.isExpr(data.getPassword());
    }

    private static void extractTokenAuthPoliciesInto(Workflow workflow, List<TaskItem> tasks,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        for (TaskItem task : tasks) {
            if (task.getTask() == null) {
                continue;
            }

            // Extract from call tasks (HTTP, gRPC, A2A, OpenAPI)
            extractFromCallTask(workflow, task, tokenAuthPolicies);

            // Extract from control flow tasks (fork, for, try, do)
            extractFromForkTask(workflow, task, tokenAuthPolicies);
            extractFromForTask(workflow, task, tokenAuthPolicies);
            extractFromTryTask(workflow, task, tokenAuthPolicies);
            extractFromDoTask(workflow, task, tokenAuthPolicies);
        }
    }

    private static void extractFromCallTask(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (task.getTask().getCallTask() == null) {
            return;
        }

        // Extract from each call task type
        extractFromHttpCall(workflow, task, tokenAuthPolicies);
        extractFromGrpcCall(workflow, task, tokenAuthPolicies);
        extractFromA2ACall(workflow, task, tokenAuthPolicies);
        extractFromOpenApiCall(workflow, task, tokenAuthPolicies);
    }

    private static void extractFromHttpCall(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        var callTask = task.getTask().getCallTask();
        if (callTask.getCallHTTP() == null ||
                callTask.getCallHTTP().getWith() == null) {
            return;
        }

        var endpoint = callTask.getCallHTTP().getWith().getEndpoint();
        if (endpoint != null && endpoint.getEndpointConfiguration() != null) {
            extractInlinePolicy(workflow, task, endpoint.getEndpointConfiguration(), tokenAuthPolicies);
        }
    }

    private static void extractFromGrpcCall(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        var callTask = task.getTask().getCallTask();
        if (callTask.getCallGRPC() == null || callTask.getCallGRPC().getWith() == null) {
            return;
        }

        var server = callTask.getCallGRPC().getWith().getService();
        if (server != null && server.getAuthentication() != null) {
            extractInlinePolicy(workflow, task, server.getAuthentication().getAuthenticationPolicy(), tokenAuthPolicies);
        }
    }

    private static void extractFromA2ACall(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        var callTask = task.getTask().getCallTask();
        if (callTask.getCallA2A() == null || callTask.getCallA2A().getWith() == null) {
            return;
        }

        var server = callTask.getCallA2A().getWith().getServer();
        if (server != null && server.getEndpointConfiguration() != null) {
            extractInlinePolicy(workflow, task, server.getEndpointConfiguration(), tokenAuthPolicies);
        }
    }

    private static void extractFromOpenApiCall(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        var callTask = task.getTask().getCallTask();
        if (callTask.getCallOpenAPI() == null ||
                callTask.getCallOpenAPI().getWith() == null) {
            return;
        }

        var endpoint = callTask.getCallOpenAPI().getWith();
        if (endpoint != null && endpoint.getAuthentication() != null) {
            extractInlinePolicy(workflow, task, endpoint.getAuthentication().getAuthenticationPolicy(), tokenAuthPolicies);
        }
    }

    private static void extractInlinePolicy(Workflow workflow, TaskItem task,
            EndpointConfiguration endpointConfig, List<TokenAuthPolicy> tokenAuthPolicies) {
        if (endpointConfig.getAuthentication() != null) {
            extractInlinePolicy(workflow, task, endpointConfig.getAuthentication().getAuthenticationPolicy(),
                    tokenAuthPolicies);
        }
    }

    private static void extractInlinePolicy(Workflow workflow, TaskItem task,
            AuthenticationPolicyUnion policyUnion,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (policyUnion == null)
            return;

        if (policyUnion.getOAuth2AuthenticationPolicy() != null) {
            if (!containsExpressions(
                    policyUnion.getOAuth2AuthenticationPolicy().getOauth2().getOAuth2ConnectAuthenticationProperties()))
                tokenAuthPolicies.add(
                        new TokenAuthPolicy(
                                OidcNamingConvention.clientName(workflow, task),
                                task.getName(),
                                policyUnion.getOAuth2AuthenticationPolicy().getOauth2()
                                        .getOAuth2ConnectAuthenticationProperties()));
        } else if (policyUnion.getOpenIdConnectAuthenticationPolicy() != null) {
            if (!containsExpressions(
                    policyUnion.getOpenIdConnectAuthenticationPolicy().getOidc().getOpenIdConnectAuthenticationProperties()))
                tokenAuthPolicies.add(
                        new TokenAuthPolicy(
                                OidcNamingConvention.clientName(workflow, task),
                                task.getName(),
                                policyUnion.getOpenIdConnectAuthenticationPolicy().getOidc()
                                        .getOpenIdConnectAuthenticationProperties()));
        }
    }

    private static void extractFromForkTask(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (task.getTask().getForkTask() != null &&
                task.getTask().getForkTask().getFork() != null) {
            extractTokenAuthPoliciesInto(workflow,
                    task.getTask().getForkTask().getFork().getBranches(),
                    tokenAuthPolicies);
        }
    }

    private static void extractFromForTask(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (task.getTask().getForTask() != null) {
            extractTokenAuthPoliciesInto(workflow,
                    task.getTask().getForTask().getDo(),
                    tokenAuthPolicies);
        }
    }

    private static void extractFromTryTask(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (task.getTask().getTryTask() != null) {
            extractTokenAuthPoliciesInto(workflow,
                    task.getTask().getTryTask().getTry(),
                    tokenAuthPolicies);
        }
    }

    private static void extractFromDoTask(Workflow workflow, TaskItem task,
            List<TokenAuthPolicy> tokenAuthPolicies) {
        if (task.getTask().getDoTask() != null) {
            extractTokenAuthPoliciesInto(workflow,
                    task.getTask().getDoTask().getDo(),
                    tokenAuthPolicies);
        }
    }

}
