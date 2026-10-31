package com.sky.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.sky.context.BaseContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充
 * <p>
 * 对比一下两种主流做法：
 * <ul>
 *   <li>课程原版：自定义 @AutoFill 注解 + AOP 切面 + 反射遍历实体字段</li>
 *   <li>本实现：MyBatis-Plus 的 MetaObjectHandler（框架原生回调，无反射遍历、无切面）</li>
 * </ul>
 * 效果一样，但后者零侵入、性能更好，也不需要额外引 aspectj。
 * <p>
 * 注意使用 strictInsertFill：它会校验实体字段上是否标了 @TableField(fill = ...)，
 * 没标的字段不会被误填充（比如某些表没有 create_user）。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentOperatorId();

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createUser", Long.class, operatorId);
        this.strictInsertFill(metaObject, "updateUser", Long.class, operatorId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updateUser", Long.class, currentOperatorId());
    }

    /**
     * 无登录上下文时（如数据初始化、定时任务）用 0 占位
     */
    private Long currentOperatorId() {
        Long id = BaseContext.getCurrentId();
        return id == null ? 0L : id;
    }
}
