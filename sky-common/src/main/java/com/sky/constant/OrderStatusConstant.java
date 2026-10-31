package com.sky.constant;

/**
 * 订单状态常量
 */
public class OrderStatusConstant {

    /** 待付款 */
    public static final Integer PENDING_PAYMENT = 1;

    /** 待接单 */
    public static final Integer TO_BE_CONFIRMED = 2;

    /** 已接单 */
    public static final Integer CONFIRMED = 3;

    /** 派送中 */
    public static final Integer DELIVERY_IN_PROGRESS = 4;

    /** 已完成 */
    public static final Integer COMPLETED = 5;

    /** 已取消 */
    public static final Integer CANCELLED = 6;
}
