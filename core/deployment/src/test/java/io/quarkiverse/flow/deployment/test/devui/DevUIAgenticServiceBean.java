package io.quarkiverse.flow.deployment.test.devui;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class DevUIAgenticServiceBean {

    public String complex(String var1, int var2, boolean var3) {
        return "v1=" + var1 + ",v2=" + var2 + ",v3=" + var3;
    }

}
