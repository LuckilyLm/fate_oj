package com.fate.codesandbox.controller;

import com.fate.codesandbox.config.SandboxProperties;
import com.fate.codesandbox.model.ExecuteRequest;
import com.fate.codesandbox.model.ExecuteResponse;
import com.fate.codesandbox.sandbox.IsolateSandboxExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 代码沙箱接口
 */
@RestController
@RequestMapping("/")
public class MainController {

    @Resource
    private SandboxProperties sandboxProperties;

    @Resource
    private IsolateSandboxExecutor isolateSandboxExecutor;

    /**
     * 推荐入口：使用 isolate 执行代码
     */
    @PostMapping("/executeCode")
    public ExecuteResponse executeCode(@RequestBody ExecuteRequest executeRequest, HttpServletRequest request,
                                       HttpServletResponse response) {
        if (!checkAuth(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return null;
        }
        return isolateSandboxExecutor.executeCode(executeRequest);
    }

    /**
     * 兼容旧入口：内部已经改为 isolate 执行，不再使用 Docker Java API。
     */
    @PostMapping("/executeCode/docker")
    public ExecuteResponse executeCodeByDocker(@RequestBody ExecuteRequest executeRequest, HttpServletRequest request,
                                               HttpServletResponse response) {
        return executeCode(executeRequest, request, response);
    }

    /**
     * 禁用本地原生执行，避免用户代码直接运行在宿主机。
     */
    @PostMapping("/executeCode/native")
    public ExecuteResponse executeCodeByNative(HttpServletResponse response) {
        response.setStatus(HttpStatus.GONE.value());
        return null;
    }

    /**
     * 禁用旧第三方执行入口，第一版真实沙箱只支持本地 isolate worker。
     */
    @PostMapping("/executeCode/remote")
    public ExecuteResponse executeCodeByRemote(HttpServletResponse response) {
        response.setStatus(HttpStatus.GONE.value());
        return null;
    }

    private boolean checkAuth(HttpServletRequest request) {
        String auth = request.getHeader(sandboxProperties.getAuthHeader());
        return sandboxProperties.getAuthSecret().equals(auth);
    }
}
