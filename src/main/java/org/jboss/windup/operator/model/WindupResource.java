package org.jboss.windup.operator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
@Log
@Version("v1")
@Kind("Windup")
@Group("windup.jboss.org")
public class WindupResource extends CustomResource<WindupResourceSpec, WindupResourceStatus> {
  public static final String READY = "Ready";
  public static final String DEPLOYMENT = "Deployment";

  private static final long serialVersionUID = 1L;

  private WindupResourceSpec spec;
  private WindupResourceStatus status;

  @JsonIgnore
  public boolean getBooleanFromStatus(String status) {
    return Boolean.parseBoolean(status != null ? status.toLowerCase() : "false");
  }

  @JsonIgnore
  public boolean isDeploying() {
    return getBooleanFromStatus(getOrAddConditionByType(DEPLOYMENT).getStatus()); 
	}

  @JsonIgnore
	public boolean isReady() {
    return getBooleanFromStatus(getOrAddConditionByType(READY).getStatus());
  }

	public long deploymentsReady() {
		return (status.getConditions() != null) ? status.getConditions().stream()
      .filter(e -> e != null &&
              getBooleanFromStatus(e.getStatus()) &&
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
    // Setting explicitly UpperCamelCase and not directly the conversion because in Openshift UI the status field
    // does a explicit case-sensitive text comparisson : https://github.com/openshift/console/blob/master/frontend/packages/operator-lifecycle-manager/src/components/operand/index.tsx#L162
    labelProperty.setStatus(statusArg ? "True" : "False");
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

	public int desiredDeployments() {
        // windup-web-console=1 , windup-postgres=1, windup-executor=executor_desired_replicas
		return 2 + ObjectUtils.defaultIfNull(spec.getExecutor_desired_replicas(), 1);
	}

}

