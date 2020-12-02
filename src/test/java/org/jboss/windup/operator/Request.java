package org.jboss.windup.operator;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Request {
    String path;
    String method;
    String body;
}
