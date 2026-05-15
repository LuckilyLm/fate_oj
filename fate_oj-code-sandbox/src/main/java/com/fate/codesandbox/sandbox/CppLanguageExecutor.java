package com.fate.codesandbox.sandbox;

import org.springframework.stereotype.Component;

/**
 * C++ 语言执行器
 */
@Component
public class CppLanguageExecutor implements LanguageExecutor {

    @Override
    public boolean supports(String language) {
        return "c++".equalsIgnoreCase(language) || "cpp".equalsIgnoreCase(language);
    }

    @Override
    public String getSourceFileName() {
        return "Main.cpp";
    }

    @Override
    public String getCompileCommand() {
        return "g++ -std=c++17 -O2 -pipe -static -s Main.cpp -o Main";
    }

    @Override
    public String getRunCommand() {
        return "./Main";
    }
}
