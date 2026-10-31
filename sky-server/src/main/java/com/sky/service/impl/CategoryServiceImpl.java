package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.exception.BaseException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setStatus(StatusConstant.ENABLE);
        categoryMapper.insert(category);
    }

    @Override
    public PageResult pageQuery(CategoryPageQueryDTO dto) {
        Page<Category> page = new Page<>(dto.getPage(), dto.getPageSize());
        categoryMapper.selectPage(page, Wrappers.<Category>lambdaQuery()
                .like(StringUtils.hasText(dto.getName()), Category::getName, dto.getName())
                .eq(dto.getType() != null, Category::getType, dto.getType())
                .orderByAsc(Category::getSort));
        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    public void deleteById(Long id) {
        // 业务约束：分类下关联了菜品或套餐，均不能删除
        Long dishCount = dishMapper.selectCount(
                Wrappers.<Dish>lambdaQuery().eq(Dish::getCategoryId, id));
        if (dishCount != null && dishCount > 0) {
            throw new BaseException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }
        Long setmealCount = setmealMapper.selectCount(
                Wrappers.<Setmeal>lambdaQuery().eq(Setmeal::getCategoryId, id));
        if (setmealCount != null && setmealCount > 0) {
            throw new BaseException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        categoryMapper.updateById(category);
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        Category category = new Category();
        category.setId(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
    }

    @Override
    public List<Category> list(Integer type) {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(type != null, Category::getType, type)
                .orderByAsc(Category::getSort));
    }
}
