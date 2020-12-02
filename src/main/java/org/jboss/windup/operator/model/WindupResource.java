package org.jboss.windup.operator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

  @JsonIgnore
  public boolean isDeploying() {
    return Boolean.parseBoolean(getOrAddConditionByType(DEPLOYMENT).getStatus());
	}

  @JsonIgnore
	public boolean isReady() {
    return  Boolean.parseBoolean(getOrAddConditionByType(READY).getStatus());
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
    setLabelProperty(statusArg, READY);
	}

  public void setStatusDeploy(boolean statusArg) {
    setLabelProperty(statusArg, DEPLOYMENT);
  }
  
  public void setLabelProperty(boolean statusArg, String label) {
    setLabelProperty(statusArg, label, null);
  }

  public void setLabelProperty(boolean statusArg, String label, String reason) {
    WindupResourceStatusCondition labelProperty = getOrAddConditionByType(label);
    labelProperty.setStatus(Boolean.toString(statusArg));
    labelProperty.setLastTransitionTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
    if (reason != null) labelProperty.setReason(reason);
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

    public WindupResource() {
      spec = new WindupResourceSpec();
      status = new WindupResourceStatus();
    }

}

