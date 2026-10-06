package org.researchagent.exception;

import org.researchagent.common.BaseResponse;
import org.researchagent.common.ResultUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<Void> business(BusinessException error) {
        return ResultUtils.error(error.getCode(), error.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<Void> unexpected(Exception error) {
        log.error("Request failed", error);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误，请查看后端日志");
    }
}
