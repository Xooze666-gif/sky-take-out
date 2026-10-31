package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-菜品接口
 */
@RestController
@RequestMapping("/admin/dish")
public class DishController {

    @Autowired
    private DishService dishService;

    /**
     * 新增菜品（含口味）
     */
    @PostMapping
    public Result<Void> save(@RequestBody DishDTO dishDTO) {
        dishService.saveWithFlavor(dishDTO);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult> page(DishPageQueryDTO dto) {
        return Result.success(dishService.pageQuery(dto));
    }

    /**
     * 批量删除菜品
     * DELETE /admin/dish?ids=1,2,3
     */
    @DeleteMapping
    public Result<Void> delete(@RequestParam("ids") List<Long> ids) {
        dishService.deleteBatch(ids);
        return Result.success();
    }

    /**
     * 按 id 查询（编辑回显，含口味）
     */
    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable Long id) {
        return Result.success(dishService.getByIdWithFlavor(id));
    }

    /**
     * 修改菜品（含口味）
     */
    @PutMapping
    public Result<Void> update(@RequestBody DishDTO dishDTO) {
        dishService.updateWithFlavor(dishDTO);
        return Result.success();
    }

    /**
     * 起售/停售
     */
    @PostMapping("/status/{status}")
    public Result<Void> startOrStop(@PathVariable Integer status, Long id) {
        dishService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 按分类查询（套餐新增时选菜品用）
     */
    @GetMapping("/list")
    public Result<List<DishVO>> list(Long categoryId) {
        return Result.success(dishService.listByCategoryId(categoryId));
    }
}
