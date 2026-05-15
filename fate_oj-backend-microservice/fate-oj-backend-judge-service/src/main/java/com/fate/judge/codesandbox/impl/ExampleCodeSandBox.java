package com.fate.judge.codesandbox.impl;


import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.fate.common.ErrorCode;
import com.fate.exception.BusinessException;
import com.fate.judge.codesandbox.CodeSandBox;
import com.fate.model.codesandbox.ExecuteRequest;
import com.fate.model.codesandbox.ExecuteResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 示例代码沙盒实现类（跑通测试用例）
 * @Author: Fate
 * @Date: 2024/7/2 13:14
 **/

@Slf4j
public class ExampleCodeSandBox implements CodeSandBox
{
    private static final String AUTH_REQUEST_HEADER = "auth";

    private final String codeSandBoxUrl;

    private final String authSecret;

    public ExampleCodeSandBox(String codeSandBoxUrl, String authSecret) {
        this.codeSandBoxUrl = StringUtils.removeEnd(codeSandBoxUrl, "/");
        this.authSecret = authSecret;
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        log.info("---调用原生代码沙箱执行代码---");
        String url = codeSandBoxUrl + "/executeCode/native";
        String json = JSONUtil.toJsonStr(executeRequest);
        String responseStr = HttpUtil.createPost(url)
                .header(AUTH_REQUEST_HEADER, authSecret)
                .body(json)
                .execute()
                .body();

        if(StringUtils.isBlank(responseStr)){
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR,"远程调用接口失败!" + responseStr);
        }
        return JSONUtil.toBean(responseStr, ExecuteResponse.class);
    }
}
