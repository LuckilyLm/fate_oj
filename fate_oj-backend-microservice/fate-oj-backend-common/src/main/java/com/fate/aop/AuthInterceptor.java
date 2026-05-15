package com.fate.aop;

import com.fate.annotation.AuthCheck;
import com.fate.common.ErrorCode;
import com.fate.constant.UserConstant;
import com.fate.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 权限校验切面
 */
@Aspect
@Component
public class AuthInterceptor {

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        if (StringUtils.isBlank(mustRole)) {
            return joinPoint.proceed();
        }

        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        BeanWrapper userWrapper = new BeanWrapperImpl(userObj);
        Object roleObj = userWrapper.getPropertyValue("userRole");
        String userRole = roleObj == null ? null : roleObj.toString();
        if (UserConstant.BAN_ROLE.equals(userRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserConstant.ADMIN_ROLE.equals(mustRole) && !UserConstant.ADMIN_ROLE.equals(userRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
