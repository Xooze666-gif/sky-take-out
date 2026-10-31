package com.sky.vo;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 订单统计 VO（按天）
 */
public class OrderReportVO implements Serializable {

    /** 日期 */
    private LocalDate date;

    /** 当天订单总数 */
    private Integer totalOrderCount;

    /** 当天有效订单数（已完成） */
    private Integer validOrderCount;

    /** 订单完成率 */
    private Double orderCompletionRate;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getTotalOrderCount() {
        return totalOrderCount;
    }

    public void setTotalOrderCount(Integer totalOrderCount) {
        this.totalOrderCount = totalOrderCount;
    }

    public Integer getValidOrderCount() {
        return validOrderCount;
    }

    public void setValidOrderCount(Integer validOrderCount) {
        this.validOrderCount = validOrderCount;
    }

    public Double getOrderCompletionRate() {
        return orderCompletionRate;
    }

    public void setOrderCompletionRate(Double orderCompletionRate) {
        this.orderCompletionRate = orderCompletionRate;
    }
}
