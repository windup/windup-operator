package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResource extends CustomResource {
    private WindupResourceSpec spec;
    private WindupResourceStatus status;
}

