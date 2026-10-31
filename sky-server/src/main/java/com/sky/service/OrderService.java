package com.sky.service;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

import java.util.Map;

public interface OrderService {

    /**
     * 下单（购物车转订单 + 明细，事务）
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 支付（幂等）
     *
     * @return "success" 首次支付成功；"duplicate" 重复通知已忽略
     */
    String payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 用户历史订单分页
     */
    PageResult historyOrders(int page, int pageSize, Integer status);

    /**
     * 订单详情（含明细）
     */
    OrderVO orderDetail(Long id);

    /**
     * 用户取消订单
     */
    void cancel(Long id);

    /**
     * 再来一单（把订单明细重新加入购物车）
     */
    void repetition(Long id);

    /**
     * 用户催单
     */
    void reminder(Long id);

    /**
     * 商家端：订单搜索（分页 + 多条件）
     */
    PageResult conditionSearch(OrdersPageQueryDTO dto);

    /**
     * 商家端：各状态订单数量统计
     */
    Map<Integer, Long> statistics();

    /**
     * 商家端：接单
     */
    void confirm(Orders orders);

    /**
     * 商家端：拒单
     */
    void rejection(Orders orders);

    /**
     * 商家端：取消订单
     */
    void adminCancel(Orders orders);

    /**
     * 商家端：派送
     */
    void delivery(Long id);

    /**
     * 商家端：完成
     */
    void complete(Long id);
}

