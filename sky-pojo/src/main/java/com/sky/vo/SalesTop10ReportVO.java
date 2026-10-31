package com.sky.vo;

import java.io.Serializable;

/**
 * 销量 Top10 报表 VO
 */
public class SalesTop10ReportVO implements Serializable {

    /** 商品名称（菜品/套餐） */
    private String name;

    /** 销量 */
    private Integer number;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
