package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResource extends CustomResource {
  public static final String READY = "Ready";
  public static final String DEPLOYMENT = "Deployment";

  private static final long serialVersionUID = 1L;

  private WindupResourceSpec spec;
  private WindupResourceStatus status;

  public boolean isDeploying() {
    return status != null && status.getConditions() != null &&
      status.getConditions().stream()
        .anyMatch(e -> DEPLOYMENT.equalsIgnoreCase(e.getReason()) && Boolean.parseBoolean(e.getStatus()));
	}

	public boolean isReady() {
    return status != null &&
           status.getConditions() != null &&
           status.getConditions().stream()
              .anyMatch(e -> e != null &&
                      READY.equalsIgnoreCase(e.getType()) &&
                      Boolean.parseBoolean(e.getStatus()));
  }

	public long deploymentsReady() {
		return (status.getConditions() != null) ? status.getConditions().stream()
      .filter(e -> e != null &&
              Boolean.parseBoolean(e.getStatus()) &&
              DEPLOYMENT.equalsIgnoreCase(e.getReason()))
			.count() : 0;
  }

  public void initStatus() {
    status = new WindupResourceStatus();
    status.setConditions(new ArrayList<>());
  }
  
  public void setReady(boolean statusArg) {
		status.getOrAddConditionByType(READY).setStatus(Boolean.toString(statusArg));
	}

  public void setStatusDeploy(boolean statusArg) {
    status.getOrAddConditionByType(DEPLOYMENT).setStatus(Boolean.toString(statusArg));
  }
}

