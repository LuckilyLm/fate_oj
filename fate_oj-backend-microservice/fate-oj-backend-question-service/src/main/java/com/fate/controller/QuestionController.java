package com.fate.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fate.annotation.AuthCheck;
import com.fate.common.BaseResponse;
import com.fate.common.DeleteRequest;
import com.fate.common.ErrorCode;
import com.fate.common.ResultUtils;
import com.fate.constant.UserConstant;
import com.fate.exception.BusinessException;
import com.fate.exception.ThrowUtils;
import com.fate.model.dto.question.*;
import com.fate.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.fate.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.fate.model.entity.Question;
import com.fate.model.entity.QuestionSubmit;
import com.fate.model.entity.User;
import com.fate.model.vo.QuestionSubmitVO;
import com.fate.model.vo.QuestionVO;
import com.fate.service.QuestionService;
import com.fate.service.QuestionSubmitService;
import com.fate.feignclient.UserFeinClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Author: Fate
 * @Date: 2024/6/30 23:57
 **/

@RestController
@RequestMapping("/")
@Slf4j
public class QuestionController {

    @Resource
    private UserFeinClient userFeinClient;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    // region 题目增删改查

    /**
     * 创建
     * @param questionAddRequest 题目添加请求
     * @param request 请求
     * @return 结果类 返回新建题目的id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        if (questionAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        // 复制属性
        BeanUtils.copyProperties(questionAddRequest, question);

        // 标签列表转json字符串
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 判题用例列表转json字符串
        List<JudgeCase> judgeCase = questionAddRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        // 判题配置转json字符串
        JudgeConfig judgeConfig = questionAddRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, true);

        // 获取当前登录用户
        User loginUser = userFeinClient.getLoginUser(request);
        boolean result = false;
        if(loginUser != null){
            // 设置创建题目用户id
            question.setUserId(loginUser.getId());
            result = questionService.save(question);
        }
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(question.getId());
    }

    /**
     * 删除
     * @param deleteRequest 删除请求
     * @param request 请求
     * @return 结果类 是否成功删除
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前登录用户
        User user = userFeinClient.getLoginUser(request);
        // 获取要删除的题目id
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userFeinClient.isAdmin(user)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除题目
        boolean result = questionService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 更新（仅管理员）
     * @param questionUpdateRequest 题目更新请求
     * @return 结果 是否成功更新
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        // 复制属性
        BeanUtils.copyProperties(questionUpdateRequest, question);
        // 标签列表转json字符串
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 判题用例列表转json字符串
        List<JudgeCase> judgeCase = questionUpdateRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        // 判题配置转json字符串
        JudgeConfig judgeConfig = questionUpdateRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        long id = questionUpdateRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     * @param id 题目id
     * @return 题目视图
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(questionService.getQuestionVO(question,request));
    }

    /**
     * 分页获取列表（仅管理员）
     * @param questionQueryRequest 查询请求
     * @return 结果类 题目分页列表
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取列表（封装类）
     * @param questionQueryRequest 查询请求
     * @param request 请求
     * @return 结果类 题目分页列表的封装类
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前用户创建的资源列表
     * @param questionQueryRequest 查询请求
     * @param request 请求
     * @return 结果类 用户创建题目的分页列表的封装类
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
            HttpServletRequest request) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userFeinClient.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }


    /**
     * 编辑（用户）
     * @param questionEditRequest 题目编辑请求
     * @param request 请求
     * @return 结果 是否成功编辑
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        // 标签列表转json字符串
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 判题用例列表转json字符串
        List<JudgeCase> judgeCase = questionEditRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        // 判题配置转json字符串
        JudgeConfig judgeConfig = questionEditRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        User loginUser = userFeinClient.getLoginUser(request);
        long id = questionEditRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userFeinClient.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    // endregion

    // region 提交题目,获取提交信息

    @RequestMapping("/question_submit/do")
    public BaseResponse<Long> doQuestionSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest,
                                               HttpServletRequest request) {
        if(questionSubmitAddRequest == null || questionSubmitAddRequest.getQuestionId() == null
                || questionSubmitAddRequest.getQuestionId() <= 0 ){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        final User loginUser = userFeinClient.getLoginUser(request);
        long questionSubmitId = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest,loginUser);
        return ResultUtils.success(questionSubmitId) ;
    }

    /**
     * 分页获取题目提交信息
     * @param questionSubmitQueryRequest 查询条件
     * @param request 请求
     * @return 提交题目信息
     */
    @PostMapping("/question_submit/list/page")
    public BaseResponse<Page<QuestionSubmitVO>> listQuestionSubmitByPage(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
                                                                         HttpServletRequest request) {
        if (questionSubmitQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        final User loginUser = userFeinClient.getLoginUser(request);
        // 获取原始的题目提交信息
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        // 返回脱敏后的题目提交信息
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVOPage(questionSubmitPage, loginUser));
    }
    // endregion
}
