package com.fate.judge.strategy.impl;

import cn.hutool.json.JSONUtil;
import com.fate.judge.strategy.JudgeContext;
import com.fate.judge.strategy.JudgeStrategy;
import com.fate.model.dto.question.JudgeCase;
import com.fate.model.dto.question.JudgeConfig;
import com.fate.model.dto.questionsubmit.JudgeInfo;
import com.fate.model.entity.Question;
import com.fate.model.enums.JudgeInfoMessageEnum;

import java.util.List;

/**
 * 默认的判题策略
 * @Author: Fate
 * @Date: 2024/7/2 18:09
 **/
public class DefaultJudgeStrategy implements JudgeStrategy
{
    /**
     * 执行判题
     * @param judgeContext 判题所需的上下文信息
     * @return 判题结果
     */
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {

        JudgeInfo judgeInfo = judgeContext.getJudgeInfo();
        List<String> inputList = judgeContext.getInputList();
        List<String> outputList = judgeContext.getOutputList();
        List<JudgeCase> judgeCaseList = judgeContext.getJudgeCaseList();
        Question question = judgeContext.getQuestion();

        // 获取执行代码的返回结果信息
        Long memory = judgeInfo.getMemory();
        Long time = judgeInfo.getTime();

        // 设置判题结果信息, 默认为 ACCEPTED
        JudgeInfo judgeInfoResult = new JudgeInfo();
        judgeInfoResult.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        judgeInfoResult.setMemory(memory);
        judgeInfoResult.setTime(time);

        if (!JudgeInfoMessageEnum.ACCEPTED.getText().equals(judgeInfo.getMessage())
                && !JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())
                && !"代码运行成功".equals(judgeInfo.getMessage())) {
            judgeInfoResult.setMessage(judgeInfo.getMessage());
            return judgeInfoResult;
        }

        // 判断输出是否与测试输出用例一致
        if(outputList.size() != inputList.size()){;
            judgeInfoResult.setMessage(JudgeInfoMessageEnum.WRONG_ANSWER.getValue());
            return judgeInfoResult;
        }

        for (int i = 0; i < judgeCaseList.size(); i++) {
            JudgeCase judgeCase = judgeCaseList.get(i);
            if(!judgeCase.getOutput().equals(outputList.get(i))){
                judgeInfoResult.setMessage(JudgeInfoMessageEnum.WRONG_ANSWER.getValue());
                return judgeInfoResult;
            }
        }

        // 获取题目的判题配置, 判断题目限制信息
        String judgeConfig = question.getJudgeConfig();
        JudgeConfig questionJudgeConfig = JSONUtil.toBean(judgeConfig, JudgeConfig.class);
        // 是否超出内存限制
        if(questionJudgeConfig != null && questionJudgeConfig.getMemoryLimit() != null
                && memory > questionJudgeConfig.getMemoryLimit()){
            judgeInfoResult.setMessage(JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED.getValue());
            return judgeInfoResult;
        }
        // 是否超出时间限制
        if(questionJudgeConfig != null && questionJudgeConfig.getTimeLimit() != null
                && time > questionJudgeConfig.getTimeLimit()){
            judgeInfoResult.setMessage(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue());
            return judgeInfoResult;
        }

        // 返回判题结果
        return judgeInfoResult;
    }
}
