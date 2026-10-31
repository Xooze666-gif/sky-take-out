package com.sky.context;

/**
 * 基于 ThreadLocal 的当前登录人上下文
 * <p>
 * 为什么用它：Controller → Service → Mapper 一路往下，若靠参数传递用户 id，
 * 每层方法签名都要多一个参数，侵入严重。ThreadLocal 让同一请求线程内任意位置可读。
 * <p>
 * 注意：Tomcat 线程池会复用线程，必须在请求结束时 remove，否则会串号 + 内存泄漏。
 */
public class BaseContext {

    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        THREAD_LOCAL.set(id);
    }

    public static Long getCurrentId() {
        return THREAD_LOCAL.get();
    }

    public static void removeCurrentId() {
        THREAD_LOCAL.remove();
    }
}
