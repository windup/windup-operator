package org.jboss.windup.operator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class Request {
    String path;
    String method;
    String body;
}
