package com.fate.codesandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 沙箱执行配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {

    /**
     * 请求鉴权请求头
     */
    private String authHeader = "auth";

    /**
     * 请求鉴权密钥
     */
    private String authSecret = "fate";

    /**
     * 临时工作目录
     */
    private String workDir = "tmpCode";

    /**
     * 默认运行时间限制，单位毫秒
     */
    private Long defaultTimeLimit = 3000L;

    /**
     * 默认内存限制，单位KB
     */
    private Long defaultMemoryLimit = 262144L;

    /**
     * 默认栈空间限制，单位KB
     */
    private Long defaultStackLimit = 65536L;

    /**
     * 编译时间限制，单位毫秒
     */
    private Long compileTimeLimit = 10000L;

    /**
     * 编译内存限制，单位KB
     */
    private Long compileMemoryLimit = 524288L;

    /**
     * 运行墙钟时间额外冗余，单位毫秒
     */
    private Long wallTimeExtra = 1000L;

    /**
     * 最大输出大小，单位字节
     */
    private Long maxOutputBytes = 1048576L;

    /**
     * 最大进程数
     */
    private Integer maxProcesses = 32;

    /**
     * 最大生成文件大小，单位KB
     */
    private Long maxFileSize = 65536L;
}
