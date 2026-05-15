package com.fate.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fate.common.ErrorCode;
import com.fate.constant.CommonConstant;
import com.fate.exception.BusinessException;
import com.fate.exception.ThrowUtils;
import com.fate.mapper.QuestionMapper;
import com.fate.model.dto.question.QuestionQueryRequest;
import com.fate.model.entity.Question;
import com.fate.model.entity.User;
import com.fate.model.vo.QuestionVO;
import com.fate.model.vo.UserVO;
import com.fate.service.QuestionService;

import com.fate.feignclient.UserFeinClient;
import com.fate.utils.SqlUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserFeinClient userFeinClient;

    /**
     * 校验参数
     * @param question 题目
     * @param add 是否为新建
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String title = question.getTitle();
        String content = question.getContent();
        String tags = question.getTags();
        String answer = question.getAnswer();
        String judgeCase = question.getJudgeCase();
        String judgeConfig = question.getJudgeConfig();

        // 新建时,参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
        }

        // 校验标题
        if (StringUtils.isNotBlank(title) && title.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        // 校验内容
        if(StringUtils.isNotBlank(content) && content.length() > 8192){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        // 校验答案
        if(StringUtils.isNotBlank(answer) && answer.length() > 8192){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        // 校验判题用例
        if(StringUtils.isNotBlank(judgeCase) && judgeCase.length() > 8192){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题用例过长");
        }
        // 校验判题配置
        if(StringUtils.isNotBlank(judgeConfig) && judgeConfig.length() > 8192){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题配置过长");
        }
    }

    /**
     * 获取查询包装类
     * @param questionQueryRequest 查询请求
     * @return 查询包装类
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();

        if (questionQueryRequest == null) {
            return queryWrapper;
        }

        Long id = questionQueryRequest.getId();
        Long userId = questionQueryRequest.getUserId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        List<String> tags = questionQueryRequest.getTags();
        String answer = questionQueryRequest.getAnswer();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();


        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }


    /**
     * 获取题目视图
     * @param question 题目
     * @param request 请求
     * @return 题目视图
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 1.填充题目信息
        QuestionVO questionVO = QuestionVO.objToVo(question);
        // 2. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userFeinClient.getById(userId);
        }
        UserVO userVO = userFeinClient.getUserVO(user);
        // 3. 填充创建题目的用户信息
        questionVO.setUserVO(userVO);
        return questionVO;
    }

    /**
     * 获取分页题目视图
     * @param questionPage 分页题目
     * @param request 请求
     * @return 分页题目视图
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 1. 获取创建题目的用户ID集合
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
         // 2. 根据用户ID进行分组  用户ID为键  对应用户ID的用户信息列表为值
        Map<Long, List<User>> userIdUserListMap = userFeinClient.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 3. 根据创建题目用户ID获取对应用户信息的用户视图对象
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = QuestionVO.objToVo(question);
            Long userId = question.getUserId();
            User user = null;
            // 如果用户ID存在于userIdUserListMap中,则获取用户信息
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 根据用户信息获取用户视图对象并填充用户视图对象到题目视图对象中
            questionVO.setUserVO(userFeinClient.getUserVO(user));
            return questionVO;
        }).collect(Collectors.toList());
        // 4. 填充分页题目视图对象并返回
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

}




