package com.sky.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 套餐-菜品关系实体（多对多中间表）
 * <p>
 * name / price 是从 dish 表复制的「快照」，保证套餐历史价格不随菜品改价而变化。
 */
@TableName("setmeal_dish")
public class SetmealDish implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long setmealId;

    private Long dishId;

    /** 菜品名称（快照） */
    private String name;

    /** 菜品单价（快照） */
    private BigDecimal price;

    /** 份数 */
    private Integer copies;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSetmealId() {
        return setmealId;
    }

    public void setSetmealId(Long setmealId) {
        this.setmealId = setmealId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getCopies() {
        return copies;
    }

    public void setCopies(Integer copies) {
        this.copies = copies;
    }
}
