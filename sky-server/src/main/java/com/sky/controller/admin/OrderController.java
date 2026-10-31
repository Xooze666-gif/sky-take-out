package com.sky.controller.admin;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端-订单接口
 */
@RestController
@RequestMapping("/admin/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 订单搜索（分页 + 多条件）
     */
    @GetMapping("/conditionSearch")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO dto) {
        return Result.success(orderService.conditionSearch(dto));
    }

    /**
     * 各状态订单数量统计
     */
    @GetMapping("/statistics")
    public Result<Map<Integer, Long>> statistics() {
        return Result.success(orderService.statistics());
    }

    /**
     * 订单详情
     */
    @GetMapping("/details/{id}")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.orderDetail(id));
    }

    /**
     * 接单
     */
    @PutMapping("/confirm")
    public Result<Void> confirm(@RequestBody Orders orders) {
        orderService.confirm(orders);
        return Result.success();
    }

    /**
     * 拒单
     */
    @PutMapping("/rejection")
    public Result<Void> rejection(@RequestBody Orders orders) {
        orderService.rejection(orders);
        return Result.success();
    }

    /**
     * 取消订单
     */
    @PutMapping("/cancel")
    public Result<Void> cancel(@RequestBody Orders orders) {
        orderService.adminCancel(orders);
        return Result.success();
    }

    /**
     * 派送
     */
    @PutMapping("/delivery/{id}")
    public Result<Void> delivery(@PathVariable Long id) {
        orderService.delivery(id);
        return Result.success();
    }

    /**
     * 完成
     */
    @PutMapping("/complete/{id}")
    public Result<Void> complete(@PathVariable Long id) {
        orderService.complete(id);
        return Result.success();
    }
}
