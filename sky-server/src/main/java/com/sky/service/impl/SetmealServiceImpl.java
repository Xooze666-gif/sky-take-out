package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        if (setmeal.getStatus() == null) {
            setmeal.setStatus(StatusConstant.ENABLE);
        }
        setmealMapper.insert(setmeal);

        insertSetmealDishes(setmeal.getId(), setmealDTO.getSetmealDishes());
    }

    @Override
    public PageResult pageQuery(SetmealPageQueryDTO dto) {
        Page<SetmealVO> page = new Page<>(dto.getPage(), dto.getPageSize());
        setmealMapper.pageQuery(page, dto.getName(), dto.getCategoryId(), dto.getStatus());
        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        // 起售中的套餐不能删
        Long onSale = setmealMapper.selectCount(Wrappers.<Setmeal>lambdaQuery()
                .in(Setmeal::getId, ids)
                .eq(Setmeal::getStatus, StatusConstant.ENABLE));
        if (onSale != null && onSale > 0) {
            throw new BaseException(MessageConstant.SETMEAL_ON_SALE);
        }

        setmealDishMapper.delete(Wrappers.<SetmealDish>lambdaQuery()
                .in(SetmealDish::getSetmealId, ids));
        setmealMapper.deleteBatchIds(ids);
    }

    @Override
    public SetmealVO getByIdWithDish(Long id) {
        Setmeal setmeal = setmealMapper.selectById(id);
        if (setmeal == null) {
            throw new BaseException(MessageConstant.SETMEAL_NOT_FOUND);
        }
        SetmealVO vo = new SetmealVO();
        BeanUtils.copyProperties(setmeal, vo);
        vo.setSetmealDishes(setmealDishMapper.selectList(Wrappers.<SetmealDish>lambdaQuery()
                .eq(SetmealDish::getSetmealId, id)));
        return vo;
    }

    @Override
    @Transactional
    public void updateWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.updateById(setmeal);

        // 关联菜品「先删后插」全量替换
        setmealDishMapper.delete(Wrappers.<SetmealDish>lambdaQuery()
                .eq(SetmealDish::getSetmealId, setmealDTO.getId()));
        insertSetmealDishes(setmealDTO.getId(), setmealDTO.getSetmealDishes());
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        // 起售前校验：套餐内所有菜品必须处于起售状态
        if (StatusConstant.ENABLE.equals(status)) {
            List<SetmealDish> dishes = setmealDishMapper.selectList(
                    Wrappers.<SetmealDish>lambdaQuery().eq(SetmealDish::getSetmealId, id));
            if (!CollectionUtils.isEmpty(dishes)) {
                List<Long> dishIds = dishes.stream()
                        .map(SetmealDish::getDishId).collect(Collectors.toList());
                Long stopped = dishMapper.selectCount(Wrappers.<Dish>lambdaQuery()
                        .in(Dish::getId, dishIds)
                        .ne(Dish::getStatus, StatusConstant.ENABLE));
                if (stopped != null && stopped > 0) {
                    throw new BaseException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }

        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.updateById(setmeal);
    }

    @Override
    public List<SetmealVO> listByCategoryId(Long categoryId) {
        List<Setmeal> setmeals = setmealMapper.selectList(Wrappers.<Setmeal>lambdaQuery()
                .eq(Setmeal::getCategoryId, categoryId)
                .eq(Setmeal::getStatus, StatusConstant.ENABLE)
                .orderByAsc(Setmeal::getId));

        List<SetmealVO> result = new ArrayList<>();
        for (Setmeal setmeal : setmeals) {
            SetmealVO vo = new SetmealVO();
            BeanUtils.copyProperties(setmeal, vo);
            result.add(vo);
        }
        return result;
    }

    /**
     * 补全菜品快照（name/price）并批量插入 setmeal_dish
     * <p>
     * 为什么不在前端传 name/price：前端是不可信的，菜品名称/价格必须以后端 dish 表为准，
     * 否则会出现「套餐里菜价和菜单对不上」的脏数据。
     * 一次性 selectBatchIds 查全部菜品，避免循环查询的 N+1 问题。
     */
    private void insertSetmealDishes(Long setmealId, List<SetmealDish> setmealDishes) {
        if (CollectionUtils.isEmpty(setmealDishes)) {
            return;
        }

        List<Long> dishIds = setmealDishes.stream()
                .map(SetmealDish::getDishId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        Map<Long, Dish> dishMap = dishIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : dishMapper.selectBatchIds(dishIds).stream()
                        .collect(Collectors.toMap(Dish::getId, Function.identity()));

        for (SetmealDish sd : setmealDishes) {
            Dish dish = dishMap.get(sd.getDishId());
            if (dish == null) {
                throw new BaseException("套餐内包含不存在的菜品");
            }
            sd.setId(null);          // 防前端带 id 篡改
            sd.setSetmealId(setmealId);
            sd.setName(dish.getName());
            sd.setPrice(dish.getPrice());
            setmealDishMapper.insert(sd);
        }
    }
}
