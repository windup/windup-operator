package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResourceStatus implements KubernetesResource {
	private static final long serialVersionUID = 1L;

	private List<WindupResourceStatusCondition> conditions;

	public boolean isReady() {
		return Boolean.parseBoolean(conditions.stream()
			.filter(e -> "Ready".equalsIgnoreCase(e.getType()))
													.findFirst()
													.map(WindupResourceStatusCondition::getStatus)
													.orElse(Boolean.FALSE.toString()));
	}

	public long deploymentsReady() {
		return conditions.stream().filter(e -> Boolean.parseBoolean(e.getStatus()) && "Deployment".equalsIgnoreCase(e.getReason())).count();
	}

	public Optional<WindupResourceStatusCondition> getConditionByType(String type) {
		return conditions.stream()
			.filter(e -> type.equalsIgnoreCase(e.getType()))
			.findFirst();
	}

	public WindupResourceStatusCondition getOrAddConditionByType(String type) {
		Optional<WindupResourceStatusCondition> condition = getConditionByType(type);
		if (!condition.isPresent()) {
			condition = Optional.of(WindupResourceStatusCondition.builder()
			.type(type)
			.lastTransitionTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
		  .build());
		  conditions.add(condition.get());
		}
		return condition.get();
	}

	public void setReady(boolean status) {
		getOrAddConditionByType("Ready").setStatus(Boolean.toString(status));
	}
}

