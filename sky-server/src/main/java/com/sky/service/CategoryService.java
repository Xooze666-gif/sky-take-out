package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {

    /**
     * 新增分类
     */
    void save(CategoryDTO categoryDTO);

    /**
     * 分页查询
     */
    PageResult pageQuery(CategoryPageQueryDTO dto);

    /**
     * 删除分类
     */
    void deleteById(Long id);

    /**
     * 修改分类
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 启用/禁用分类
     */
    void startOrStop(Integer status, Long id);

    /**
     * 按类型查询分类列表（下拉框用）
     */
    List<Category> list(Integer type);
}
