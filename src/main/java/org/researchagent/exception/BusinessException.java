package org.researchagent.exception;

public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code.getCode();
    }
    public BusinessException(ErrorCode code) {
        this(code, code.name());
    }
    public int getCode() { return code; }
}
