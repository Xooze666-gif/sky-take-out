package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     * 新增菜品 + 口味。
     * <p>
     * 为什么加事务：dish 与 dish_flavor 是两张表，若口味插入失败而菜品已插入，
     * 会出现「有菜品无口味」的脏数据。@Transactional 让两步要么都成功、要么都回滚。
     */
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dish.setStatus(StatusConstant.ENABLE);
        dishMapper.insert(dish);

        // 主键回填后，口味才知道挂在哪个菜品下
        insertFlavors(dish.getId(), dishDTO.getFlavors());
    }

    @Override
    public PageResult pageQuery(DishPageQueryDTO dto) {
        Page<DishVO> page = new Page<>(dto.getPage(), dto.getPageSize());
        dishMapper.pageQuery(page, dto.getName(), dto.getCategoryId(), dto.getStatus());
        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        // 起售中的菜品不能删
        Long onSale = dishMapper.selectCount(Wrappers.<Dish>lambdaQuery()
                .in(Dish::getId, ids)
                .eq(Dish::getStatus, StatusConstant.ENABLE));
        if (onSale != null && onSale > 0) {
            throw new BaseException(MessageConstant.DISH_ON_SALE);
        }

        // 先删口味，再删菜品（顺序反了口味会变孤儿数据）
        dishFlavorMapper.delete(Wrappers.<DishFlavor>lambdaQuery()
                .in(DishFlavor::getDishId, ids));
        dishMapper.deleteBatchIds(ids);
    }

    @Override
    public DishVO getByIdWithFlavor(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        DishVO vo = new DishVO();
        BeanUtils.copyProperties(dish, vo);
        vo.setFlavors(dishFlavorMapper.selectList(Wrappers.<DishFlavor>lambdaQuery()
                .eq(DishFlavor::getDishId, id)));
        return vo;
    }

    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.updateById(dish);

        // 口味采取「先删后插」全量替换，避免比对新旧差异的复杂逻辑
        dishFlavorMapper.delete(Wrappers.<DishFlavor>lambdaQuery()
                .eq(DishFlavor::getDishId, dishDTO.getId()));
        insertFlavors(dishDTO.getId(), dishDTO.getFlavors());
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setStatus(status);
        dishMapper.updateById(dish);
    }

    @Override
    public List<DishVO> listByCategoryId(Long categoryId) {
        List<Dish> dishes = dishMapper.selectList(Wrappers.<Dish>lambdaQuery()
                .eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getStatus, StatusConstant.ENABLE)
                .orderByAsc(Dish::getId));

        List<DishVO> result = new ArrayList<>();
        for (Dish dish : dishes) {
            DishVO vo = new DishVO();
            BeanUtils.copyProperties(dish, vo);
            result.add(vo);
        }
        return result;
    }

    private void insertFlavors(Long dishId, List<DishFlavor> flavors) {
        if (CollectionUtils.isEmpty(flavors)) {
            return;
        }
        for (DishFlavor flavor : flavors) {
            flavor.setId(null);      // 强制走自增，防止前端带 id 篡改
            flavor.setDishId(dishId);
            dishFlavorMapper.insert(flavor);
        }
    }
}
