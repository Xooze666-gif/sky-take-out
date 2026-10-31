package com.sky.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单分页查询入参（用户端历史订单 / 商家端订单搜索）
 */
public class OrdersPageQueryDTO implements Serializable {

    private int page = 1;

    private int pageSize = 10;

    /** 订单状态（可空） */
    private Integer status;

    /** 订单号（商家端搜索） */
    private String number;

    /** 手机号（商家端搜索） */
    private String phone;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(LocalDateTime beginTime) {
        this.beginTime = beginTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
