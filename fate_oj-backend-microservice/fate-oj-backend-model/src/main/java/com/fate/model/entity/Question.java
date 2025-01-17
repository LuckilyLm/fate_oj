package com.fate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目表
 * @TableName question
 */
@TableName(value ="question")
@Data
public class Question implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 题目内容
     */
    private String content;

    /**
     * 标签列表(json数组)
     */
    private String tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 题目提交次数
     */
    private Integer submitNum;

    /**
     * 题目通过次数
     */
    private Integer acceptNum;

    /**
     * 判题用例(json数组)
     */
    private String judgeCase;

    /**
     * 判题配置(json对象)
     */
    private String judgeConfig;

    /**
     * 点赞次数
     */
    private Integer thumbNum;

    /**
     * 收藏次数
     */
    private Integer favorNum;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}