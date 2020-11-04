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
    private static final long serialVersionUID = 1L;
    
    private WindupResourceSpec spec;
    private WindupResourceStatus status;

    public boolean isDeploying() {
		return status.getConditions() == null ||
			status.getConditions().stream()
				.noneMatch(e -> "Deployment".equalsIgnoreCase(e.getReason()) && Boolean.parseBoolean(e.getStatus()));
	}
}

