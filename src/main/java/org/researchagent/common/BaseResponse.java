package org.researchagent.common;

public record BaseResponse<T>(int code, T data, String message) { }
