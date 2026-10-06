package org.researchagent.common;

import org.researchagent.exception.ErrorCode;

public final class ResultUtils {
    private ResultUtils() { }
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }
    public static BaseResponse<Void> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }
    public static BaseResponse<Void> error(ErrorCode code, String message) {
        return error(code.getCode(), message);
    }
}
