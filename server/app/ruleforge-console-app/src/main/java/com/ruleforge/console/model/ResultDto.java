package com.ruleforge.console.model;

import lombok.Data;

/**
 * @author Fred
 * @since 2025/8/26 13:36
 */
@Data
public class ResultDto<T> {

    private final T data;
    private final boolean status;
    private final String msg;

    public ResultDto(T data) {
        this.data = data;
        this.status = true;
        this.msg = null;
    }

    public ResultDto(String msg) {
        this.data = null;
        this.status = false;
        this.msg = msg;
    }

    public ResultDto(T data, boolean status, String msg) {
        this.data = data;
        this.status = status;
        this.msg = msg;
    }
}
