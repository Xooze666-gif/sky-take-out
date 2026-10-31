package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart cart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, cart);
        cart.setUserId(userId);

        // 菜品和套餐必须二选一
        if (cart.getDishId() == null && cart.getSetmealId() == null) {
            throw new BaseException("dishId 和 setmealId 至少传一个");
        }

        // 查是否已存在相同条目
        ShoppingCart existing = shoppingCartMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .eq(cart.getDishId() != null, ShoppingCart::getDishId, cart.getDishId())
                .eq(cart.getSetmealId() != null, ShoppingCart::getSetmealId, cart.getSetmealId())
                .eq(cart.getDishFlavor() != null, ShoppingCart::getDishFlavor, cart.getDishFlavor()));

        if (existing != null) {
            // 已存在：数量 +1，并刷新单价（以数据库最新价为准）
            existing.setNumber(existing.getNumber() + 1);
            existing.setAmount(currentAmount(existing));
            shoppingCartMapper.updateById(existing);
            return;
        }

        // 不存在：补全商品信息后新增
        fillGoodsInfo(cart);
        cart.setNumber(1);
        cart.setCreateTime(LocalDateTime.now());
        shoppingCartMapper.insert(cart);
    }

    @Override
    public List<ShoppingCart> list() {
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.selectList(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .orderByDesc(ShoppingCart::getCreateTime));
    }

    @Override
    public void clean() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));
    }

    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart existing = shoppingCartMapper.selectOne(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .eq(shoppingCartDTO.getDishId() != null, ShoppingCart::getDishId, shoppingCartDTO.getDishId())
                .eq(shoppingCartDTO.getSetmealId() != null, ShoppingCart::getSetmealId, shoppingCartDTO.getSetmealId())
                .eq(shoppingCartDTO.getDishFlavor() != null, ShoppingCart::getDishFlavor, shoppingCartDTO.getDishFlavor()));

        if (existing == null) {
            return;
        }
        if (existing.getNumber() <= 1) {
            shoppingCartMapper.deleteById(existing.getId());
        } else {
            existing.setNumber(existing.getNumber() - 1);
            shoppingCartMapper.updateById(existing);
        }
    }

    /**
     * 补全商品的名称/图片/单价（菜品查 dish 表，套餐查 setmeal 表）
     */
    private void fillGoodsInfo(ShoppingCart cart) {
        if (cart.getDishId() != null) {
            Dish dish = dishMapper.selectById(cart.getDishId());
            if (dish == null) {
                throw new BaseException("菜品不存在");
            }
            cart.setName(dish.getName());
            cart.setImage(dish.getImage());
            cart.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = setmealMapper.selectById(cart.getSetmealId());
            if (setmeal == null) {
                throw new BaseException("套餐不存在");
            }
            cart.setName(setmeal.getName());
            cart.setImage(setmeal.getImage());
            cart.setAmount(setmeal.getPrice());
        }
    }

    /**
     * 刷新单价（以数据库最新价为准）
     */
    private java.math.BigDecimal currentAmount(ShoppingCart cart) {
        if (cart.getDishId() != null) {
            Dish dish = dishMapper.selectById(cart.getDishId());
            return dish == null ? cart.getAmount() : dish.getPrice();
        }
        Setmeal setmeal = setmealMapper.selectById(cart.getSetmealId());
        return setmeal == null ? cart.getAmount() : setmeal.getPrice();
    }
}
