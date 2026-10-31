package com.sky.dto;

import java.io.Serializable;

/**
 * 支付入参
 */
public class OrdersPaymentDTO implements Serializable {

    /** 订单号 */
    private String orderNumber;

    /** 支付方式 1微信 2支付宝 */
    private Integer payMethod;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Integer getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(Integer payMethod) {
        this.payMethod = payMethod;
    }
}
