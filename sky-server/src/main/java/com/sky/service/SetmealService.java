package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {

    /**
     * 新增套餐（含关联菜品，事务）
     */
    void saveWithDish(SetmealDTO setmealDTO);

    /**
     * 分页查询
     */
    PageResult pageQuery(SetmealPageQueryDTO dto);

    /**
     * 批量删除套餐（含关联菜品，事务）
     */
    void deleteBatch(List<Long> ids);

    /**
     * 按 id 查询（回显，含关联菜品）
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 修改套餐（含关联菜品，事务）
     */
    void updateWithDish(SetmealDTO setmealDTO);

    /**
     * 起售/停售
     */
    void startOrStop(Integer status, Long id);

    /**
     * 按分类查询套餐（起售中的，C 端展示用）
     */
    List<SetmealVO> listByCategoryId(Long categoryId);
}
