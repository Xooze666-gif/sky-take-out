package com.sky.dto;

import java.io.Serializable;

/**
 * 分类新增/修改入参
 */
public class CategoryDTO implements Serializable {

    private Long id;

    /** 类型 1菜品分类 2套餐分类 */
    private Integer type;

    private String name;

    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
