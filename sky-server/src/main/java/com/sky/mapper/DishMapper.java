package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.entity.Dish;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 菜品 Mapper
 * <p>
 * 单表走 BaseMapper，多表关联（如分页带分类名）走自定义 SQL（见 DishMapper.xml）。
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 菜品分页查询（join category 取分类名）
     */
    Page<DishVO> pageQuery(Page<DishVO> page,
                           @Param("name") String name,
                           @Param("categoryId") Long categoryId,
                           @Param("status") Integer status);
}
