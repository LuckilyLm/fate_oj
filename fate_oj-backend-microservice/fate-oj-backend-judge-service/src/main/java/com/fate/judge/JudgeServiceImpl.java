package com.fate.judge;

import cn.hutool.json.JSONUtil;
import com.fate.common.ErrorCode;
import com.fate.exception.BusinessException;
import com.fate.exception.ThrowUtils;
import com.fate.judge.codesandbox.CodeSandBox;
import com.fate.judge.codesandbox.CodeSandBoxFactory;
import com.fate.judge.codesandbox.CodeSandBoxProxy;
import com.fate.judge.strategy.JudgeContext;
import com.fate.model.codesandbox.ExecuteRequest;
import com.fate.model.codesandbox.ExecuteResponse;
import com.fate.model.dto.question.JudgeConfig;
import com.fate.model.dto.question.JudgeCase;
import com.fate.model.dto.questionsubmit.JudgeInfo;
import com.fate.model.entity.Question;
import com.fate.model.entity.QuestionSubmit;
import com.fate.model.enums.JudgeInfoMessageEnum;
import com.fate.model.enums.QuestionSubmitStatusEnum;
import com.fate.feignclient.QuestionFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 判题服务实现类
 * @Author: Fate
 * @Date: 2024/7/2 17:14
 **/

@Service
public class JudgeServiceImpl implements JudgeService {

    @Value("${codesandbox.type}")
    private String codeSandBoxType;

    @Value("${codesandbox.url:http://localhost:8090}")
    private String codeSandBoxUrl;

    @Value("${codesandbox.auth-secret:fate}")
    private String codeSandBoxAuthSecret;

    @Resource
    private JudgeManager judgeManager;

    @Resource
    private QuestionFeignClient questionFeignClient;

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1. 根据提交题目id获取提交题目信息
        QuestionSubmit questionSubmit = questionFeignClient.getQuestionSubmitById(questionSubmitId);
        ThrowUtils.throwIf(questionSubmit == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交题目不存在"));

        // 根据题目id获取题目信息
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionFeignClient.getQuestionById(questionId);
        ThrowUtils.throwIf(question == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在"));

        // 2. 判断题目是否已经提交过, 如果已经提交过，不允许重复提交
        ThrowUtils.throwIf(!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue()),
                new BusinessException(ErrorCode.OPERATION_ERROR,"已经提交过，请勿重复提交"));

        // 3. 更新题目提交状态为正在判题中
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean updateResult = questionFeignClient.updateQuestionSubmitById(questionSubmitUpdate);
        ThrowUtils.throwIf(!updateResult,new BusinessException(ErrorCode.OPERATION_ERROR,"更新提交题目状态失败"));


        // 4. 调用沙箱接口执行代码, 获取执行结果
        CodeSandBox codeSandBox = CodeSandBoxFactory.newInstance(codeSandBoxType, codeSandBoxUrl, codeSandBoxAuthSecret);
        codeSandBox = new CodeSandBoxProxy(codeSandBox);
        // 获取提交的代码和编程语言
        String language = questionSubmit.getLanguage();
        String code = questionSubmit.getCode();
        // 获取题目的测试用例
        String judgeCaseStr = question.getJudgeCase();
        List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
        List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
        JudgeConfig judgeConfig = JSONUtil.toBean(question.getJudgeConfig(), JudgeConfig.class);

        ExecuteRequest executeRequest = ExecuteRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .timeLimit(judgeConfig == null ? null : judgeConfig.getTimeLimit())
                .memoryLimit(judgeConfig == null ? null : judgeConfig.getMemoryLimit())
                .stackLimit(judgeConfig == null ? null : judgeConfig.getStackLimit())
                .build();

        // 调用沙箱接口执行代码
        ExecuteResponse executeResponse = codeSandBox.executeCode(executeRequest);


        // 5. 根据执行结果进行判题
        // 封装判题所需的上下文信息
        JudgeContext judgeContext = new JudgeContext();
        // 设置代码运行的时间,内存的结果
        judgeContext.setJudgeInfo(executeResponse.getJudgeInfo());
        // 设置该题目的输入数据
        judgeContext.setInputList(inputList);
        // 从执行结果中获取输出数据
        judgeContext.setOutputList(executeResponse.getOutputList());
        // 设置该题目的测试用例
        judgeContext.setJudgeCaseList(judgeCaseList);
        // 设置该题目的题目信息
        judgeContext.setQuestion(question);
        // 设置该题目的提交信息
        judgeContext.setQuestionSubmit(questionSubmit);

        // 6. 调用判题管理器进行判题
        JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);

        // 7. 更新题目提交状态为已完成, 并保存判题信息
        QuestionSubmit questionSubmitFinish = new QuestionSubmit();
        questionSubmitFinish.setId(questionSubmitId);
        // 根据判题结果更新题目提交状态,成功或者失败
        if(JudgeInfoMessageEnum.ACCEPTED.getText().equals(judgeInfo.getMessage())
                || JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())){
            questionSubmitFinish.setStatus(QuestionSubmitStatusEnum.SUCCESS.getValue());
        }else{
            questionSubmitFinish.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
        }
        questionSubmitFinish.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        boolean result = questionFeignClient.updateQuestionSubmitById(questionSubmitFinish);
        ThrowUtils.throwIf(!result,new BusinessException(ErrorCode.OPERATION_ERROR,"更新提交题目状态失败"));

        // 8. 返回更新后的题目提交信息
        return questionFeignClient.getQuestionSubmitById(questionSubmitId);
    }
}
