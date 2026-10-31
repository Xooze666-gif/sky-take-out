package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 菜品口味实体（一个菜品对应多条口味，如 辣度/忌口/温度）
 */
@TableName("dish_flavor")
public class DishFlavor implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long dishId;

    /** 口味名称，如「辣度」「忌口」 */
    private String name;

    /** 口味数据，JSON 数组字符串，如 ["不辣","微辣","中辣"] */
    private String value;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDishId() {
        return dishId;
    }

    public void setDishId(Long dishId) {
        this.dishId = dishId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
