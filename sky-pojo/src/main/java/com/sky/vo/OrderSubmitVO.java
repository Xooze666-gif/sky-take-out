package com.sky.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单返回
 */
public class OrderSubmitVO implements Serializable {

    private Long id;

    private String orderNumber;

    private BigDecimal orderAmount;

    private LocalDateTime orderTime;

    public OrderSubmitVO() {
    }

    public OrderSubmitVO(Long id, String orderNumber, BigDecimal orderAmount, LocalDateTime orderTime) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.orderAmount = orderAmount;
        this.orderTime = orderTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderAmount = orderAmount;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime orderTime) {
        this.orderTime = orderTime;
    }
}
