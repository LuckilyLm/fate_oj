package com.fate.codesandbox.sandbox;

/**
 * 语言执行器
 */
public interface LanguageExecutor {

    boolean supports(String language);

    String getSourceFileName();

    String getCompileCommand();

    String getRunCommand();
}
