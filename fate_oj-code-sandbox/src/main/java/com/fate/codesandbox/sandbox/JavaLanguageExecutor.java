package com.fate.codesandbox.sandbox;

import org.springframework.stereotype.Component;

/**
 * Java 语言执行器
 */
@Component
public class JavaLanguageExecutor implements LanguageExecutor {

    @Override
    public boolean supports(String language) {
        return "java".equalsIgnoreCase(language);
    }

    @Override
    public String getSourceFileName() {
        return "Main.java";
    }

    @Override
    public String getCompileCommand() {
        return "javac -encoding UTF-8 Main.java";
    }

    @Override
    public String getRunCommand() {
        return "java -Dfile.encoding=UTF-8 -cp . Main";
    }
}
