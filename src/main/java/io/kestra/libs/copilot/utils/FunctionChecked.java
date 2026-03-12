package io.kestra.libs.copilot.utils;

@FunctionalInterface
public interface FunctionChecked<T, R> {
    R apply(T t) throws Exception;
}
