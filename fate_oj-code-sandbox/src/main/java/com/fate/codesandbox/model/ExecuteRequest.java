package com.fate.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: Fate
 * @Date: 2024/7/2 13:04
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest
{
    /**
     * 一组输入用例
     */
    private List<String> inputList;

    /**
     * 代码
     */
    private String code;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 时间限制，单位为毫秒
     */
    private Long timeLimit;

    /**
     * 内存限制，单位为KB
     */
    private Long memoryLimit;

    /**
     * 栈空间限制，单位为KB
     */
    private Long stackLimit;
}
