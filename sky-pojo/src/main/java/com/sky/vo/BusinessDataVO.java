package com.sky.vo;

import java.io.Serializable;

/**
 * 营业额统计 VO（按天）
 */
public class BusinessDataVO implements Serializable {

    /** 日期 */
    private String date;

    /** 营业额（当天已完成订单实收金额合计） */
    private Double turnover;

    /** 有效订单数（当天已完成订单数） */
    private Integer validOrderCount;

    /** 订单完成率 */
    private Double orderCompletionRate;

    /** 平均客单价 */
    private Double unitPrice;

    /** 新增用户数 */
    private Integer newUsers;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Double getTurnover() {
        return turnover;
    }

    public void setTurnover(Double turnover) {
        this.turnover = turnover;
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

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(Integer newUsers) {
        this.newUsers = newUsers;
    }
}
