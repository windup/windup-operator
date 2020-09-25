package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;

@JsonDeserialize
public class WindupResource extends CustomResource {

    private WindupResourceSpec spec;
    private WindupResourceStatus status;
    // getters/setters

    public WindupResourceSpec getSpec() {
        return spec;
    }

    public void setSpec(WindupResourceSpec spec) {
        this.spec = spec;
    }

    public WindupResourceStatus getStatus() {
        return status;
    }

    public void setStatus(WindupResourceStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        String name = getMetadata() != null ? getMetadata().getName() : "unknown";
        String version = getMetadata() != null ? getMetadata().getResourceVersion() : "unknown";
        return "name=" + name + " version=" + version + " value=" + spec;
    }
}

