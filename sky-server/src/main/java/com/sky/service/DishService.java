package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 新增菜品（含口味，事务）
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 分页查询（含分类名）
     */
    PageResult pageQuery(DishPageQueryDTO dto);

    /**
     * 批量删除菜品（含口味）
     */
    void deleteBatch(List<Long> ids);

    /**
     * 按 id 查询（回显，含口味）
     */
    DishVO getByIdWithFlavor(Long id);

    /**
     * 修改菜品（含口味，事务）
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 起售/停售
     */
    void startOrStop(Integer status, Long id);

    /**
     * 按分类查询菜品（起售中的）
     */
    List<DishVO> listByCategoryId(Long categoryId);
}
