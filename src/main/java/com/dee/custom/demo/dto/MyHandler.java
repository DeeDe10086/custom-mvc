package com.dee.custom.demo.dto;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

public class MyHandler {

    //存入需要调用的method的类
    private Object controller;
    //存入method
    private Method method;
    //正则
    private Pattern pattern;
    //存入参数的位置
    private Map<String, Integer> paramIndexMapping;

    public MyHandler() {
    }

    public MyHandler(Object controller, Method method, Pattern pattern, Map<String, Integer> paramIndexMapping) {
        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        this.paramIndexMapping = paramIndexMapping;
    }

    public Object getController() {
        return controller;
    }

    public void setController(Object controller) {
        this.controller = controller;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public Map<String, Integer> getParamIndexMapping() {
        return paramIndexMapping;
    }

    public void setParamIndexMapping(Map<String, Integer> paramIndexMapping) {
        this.paramIndexMapping = paramIndexMapping;
    }
}
