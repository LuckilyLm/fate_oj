package com.fate.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * SQL 工具
 */
public class SqlUtils {

    private static final String SORT_FIELD_PATTERN = "^[A-Za-z][A-Za-z0-9_]*$";

    /**
     * 校验排序字段是否合法（防止 SQL 注入）
     */
    public static boolean validSortField(String sortField) {
        if (StringUtils.isBlank(sortField)) {
            return false;
        }
        return sortField.matches(SORT_FIELD_PATTERN);
    }
}
