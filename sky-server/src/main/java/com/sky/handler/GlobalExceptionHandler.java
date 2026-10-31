package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 目标：Controller 里不写 try-catch，业务异常直接 throw，
 * 统一在这里转成 {code:0, msg:"..."}，前端只需判断 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：消息可直接给用户看 */
    @ExceptionHandler(BaseException.class)
    public Result<String> handleBusinessException(BaseException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 唯一键冲突，例如新增员工时用户名重复
     * <p>
     * 注意：Spring 会把底层 SQLIntegrityConstraintViolationException
     * 通过 SQLErrorCodeSQLExceptionTranslator 翻译成 DuplicateKeyException，
     * 所以要 catch 后者（这也是 Boot 3 + MyBatis 下的常见坑）。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> handleDuplicateKey(DuplicateKeyException ex) {
        String message = ex.getMessage();
        log.warn("唯一约束冲突: {}", message);
        if (message != null && message.contains("Duplicate entry")) {
            // 形如：Duplicate entry 'wangwu' for key 'employee.idx_username'
            int start = message.indexOf("Duplicate entry '");
            if (start >= 0) {
                int valueStart = start + "Duplicate entry '".length();
                int valueEnd = message.indexOf("'", valueStart);
                if (valueEnd > valueStart) {
                    String value = message.substring(valueStart, valueEnd);
                    return Result.error(value + " 已存在");
                }
            }
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /** 兜底：不把堆栈细节暴露给前端，但必须打日志 */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }
}
