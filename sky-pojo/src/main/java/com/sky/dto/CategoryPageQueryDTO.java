package com.sky.dto;

import java.io.Serializable;

/**
 * 分类分页查询入参
 */
public class CategoryPageQueryDTO implements Serializable {

    private int page = 1;

    private int pageSize = 10;

    private String name;

    private Integer type;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
}
