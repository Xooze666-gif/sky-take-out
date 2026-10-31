package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.entity.Setmeal;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 套餐 Mapper
 */
@Mapper
public interface SetmealMapper extends BaseMapper<Setmeal> {

    /**
     * 套餐分页查询（join category 取分类名）
     */
    Page<SetmealVO> pageQuery(Page<SetmealVO> page,
                              @Param("name") String name,
                              @Param("categoryId") Long categoryId,
                              @Param("status") Integer status);
}
