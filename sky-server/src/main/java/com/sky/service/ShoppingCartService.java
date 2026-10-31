package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 加购（同商品同口味则数量 +1，否则新增一条）
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查询当前用户购物车
     */
    List<ShoppingCart> list();

    /**
     * 清空当前用户购物车
     */
    void clean();

    /**
     * 减少数量（数量为 1 时删除该条）
     */
    void sub(ShoppingCartDTO shoppingCartDTO);
}
