package com.sky.vo;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 用户统计 VO（按天）
 */
public class UserReportVO implements Serializable {

    /** 日期 */
    private LocalDate date;

    /** 当天新增用户数 */
    private Integer newUserCount;

    /** 截止当天累计用户总数 */
    private Integer totalUserCount;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getNewUserCount() {
        return newUserCount;
    }

    public void setNewUserCount(Integer newUserCount) {
        this.newUserCount = newUserCount;
    }

    public Integer getTotalUserCount() {
        return totalUserCount;
    }

    public void setTotalUserCount(Integer totalUserCount) {
        this.totalUserCount = totalUserCount;
    }
}
