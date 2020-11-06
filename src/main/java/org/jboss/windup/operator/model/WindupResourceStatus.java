package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
@Log
public class WindupResourceStatus implements KubernetesResource {
	private static final long serialVersionUID = 1L;

	private List<WindupResourceStatusCondition> conditions = new ArrayList<>();

	public Optional<WindupResourceStatusCondition> getConditionByType(String type) {
		return conditions.stream()
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
		  	conditions.add(condition.get());
		}
		return condition.get();
	}
}

