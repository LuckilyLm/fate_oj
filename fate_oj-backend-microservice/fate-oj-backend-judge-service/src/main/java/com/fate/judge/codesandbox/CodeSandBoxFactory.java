package com.fate.judge.codesandbox;


import com.fate.judge.codesandbox.impl.ExampleCodeSandBox;
import com.fate.judge.codesandbox.impl.RemoteCodeSandBox;
import com.fate.judge.codesandbox.impl.ThirdPartyCodeSandBox;

/**
 * 代码沙箱工厂类
 * @Author: Fate
 * @Date: 2024/7/2 13:33
 **/
public class CodeSandBoxFactory
{
    /**
     * 创建代码沙盒实例
     * @param type 代码沙箱类型
     * @return 代码沙箱实例
     */
    public static CodeSandBox newInstance(String type, String codeSandBoxUrl, String authSecret){
         switch (type) {
            case "remote" :
                return new RemoteCodeSandBox(codeSandBoxUrl, authSecret);
            case "thirdParty" :
                return new ThirdPartyCodeSandBox(codeSandBoxUrl, authSecret);
            default :
                return new ExampleCodeSandBox(codeSandBoxUrl, authSecret);
        }
    }
}
