package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResourceStatus implements KubernetesResource {
	String name;
	String group;
	String kind;
	String uid;
	List<Object> causes;
	Integer retryAfterSeconds;
}

