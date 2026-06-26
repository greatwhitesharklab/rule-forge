package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V1 INVOKE action 的参数(al 动作库,V7.4.1b)。{@code ref}(只读 fact 字段引用)与 {@code value}
 * (字面量)互斥 — 复用 V1 Action value/ref 语义,不引入 CEL/script。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Arg {
    private String name;
    private Object value;
    private String ref;

    public Arg() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }
}
