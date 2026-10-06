package org.researchagent.exception;

public enum ErrorCode {
    PARAMS_ERROR(40000), NOT_FOUND_ERROR(40400), SYSTEM_ERROR(50000), OPERATION_ERROR(50001);
    private final int code;
    ErrorCode(int code) { this.code = code; }
    public int getCode() { return code; }
}
