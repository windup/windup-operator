package org.jboss.windup.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "related.image")
public interface Config {

    @WithName("windup.web")
    String webImage();

    @WithName("windup.web.executor")
    String executorImage();

    @WithName("postgresql")
    String dbImage();

    @WithName("image-pull-policy")
    String imagePullPolicy();
}
