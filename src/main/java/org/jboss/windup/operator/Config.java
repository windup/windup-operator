package org.jboss.windup.operator;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "operator")
public interface Config {

    Windup windup();

    interface Windup {
        String webImage();
        String executorImage();
        String dbImage();

        String imagePullPolicy();
    }
}
