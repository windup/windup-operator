package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
@Log
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
		getOrAddConditionByType(READY).setStatus(Boolean.toString(statusArg));
	}

  public void setStatusDeploy(boolean statusArg) {
    getOrAddConditionByType(DEPLOYMENT).setStatus(Boolean.toString(statusArg));
  }

  public Optional<WindupResourceStatusCondition> getConditionByType(String type) {
		return status.getConditions().stream()
			.filter(e -> e != null && type.equalsIgnoreCase(e.getType()))
			.findFirst();
	}

	public WindupResourceStatusCondition getOrAddConditionByType(String type) {
		Optional<WindupResourceStatusCondition> condition = getConditionByType(type);
		if (!condition.isPresent() ) {
			log.info(" Condition " + condition + " is NOT present ");
			condition = Optional.of(WindupResourceStatusCondition.builder()
				.type(type)
				.reason("").message("")
				.lastTransitionTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
				.build());
			log.info(" Adding condition :" + condition);
		  	status.getConditions().add(condition.get());
		}
		return condition.get();
	}
}

