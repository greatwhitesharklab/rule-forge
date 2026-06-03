package com.ruleforge.console.model;

import lombok.Data;

@Data
public class BaseProcessDto<T> {
    public BaseProcessDto(Double progress) {
        this.progress = progress;
        this.finish = false;
    }

    public BaseProcessDto(T data) {
        this.data = data;
        this.finish = true;
    }

    protected Double progress;
    protected boolean finish;
    protected String errorMessage;

    private T data;
}
