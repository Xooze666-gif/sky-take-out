package com.sky.dto;

import java.io.Serializable;

/**
 * 员工分页查询入参
 */
public class EmployeePageQueryDTO implements Serializable {

    private int page = 1;

    private int pageSize = 10;

    /** 按姓名模糊查询 */
    private String name;

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
}
