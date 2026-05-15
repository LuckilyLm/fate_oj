package com.fate.codesandbox.sandbox;

/**
 * 判题状态文本
 */
public interface JudgeStatus {

    String ACCEPTED = "Accepted";

    String COMPILE_ERROR = "Compile Error";

    String TIME_LIMIT_EXCEEDED = "Time Limit Exceeded";

    String MEMORY_LIMIT_EXCEEDED = "Memory Limit Exceeded";

    String RUNTIME_ERROR = "Runtime Error";

    String SYSTEM_ERROR = "System Error";

    String OUTPUT_LIMIT_EXCEEDED = "Output Limit Exceeded";
}
