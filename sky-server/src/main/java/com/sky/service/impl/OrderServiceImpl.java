package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.constant.OrderStatusConstant;
import com.sky.constant.PayStatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.BaseException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter NUMBER_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        // 1. 地址校验（必须属于当前用户）
        AddressBook addressBook = addressBookMapper.selectById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null || !addressBook.getUserId().equals(userId)) {
            throw new BaseException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 2. 购物车校验
        List<ShoppingCart> carts = shoppingCartMapper.selectList(
                Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId));
        if (CollectionUtils.isEmpty(carts)) {
            throw new BaseException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 3. 构建订单主表
        Orders order = new Orders();
        order.setNumber(generateOrderNumber());
        order.setStatus(OrderStatusConstant.PENDING_PAYMENT);
        order.setUserId(userId);
        order.setAddressBookId(addressBook.getId());
        order.setOrderTime(LocalDateTime.now());
        order.setPayMethod(ordersSubmitDTO.getPayMethod());
        order.setPayStatus(PayStatusConstant.UN_PAID);
        order.setAmount(ordersSubmitDTO.getAmount());
        order.setRemark(ordersSubmitDTO.getRemark());
        // 地址快照：防止用户后续改地址影响历史订单
        order.setPhone(addressBook.getPhone());
        order.setConsignee(addressBook.getConsignee());
        order.setAddress(buildAddress(addressBook));
        order.setEstimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime());
        order.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus());
        order.setTablewareNumber(ordersSubmitDTO.getTablewareNumber());
        order.setTablewareStatus(ordersSubmitDTO.getTablewareStatus());
        order.setPackAmount(ordersSubmitDTO.getPackAmount());
        orderMapper.insert(order);

        // 4. 订单明细（购物车 → 明细快照）
        for (ShoppingCart cart : carts) {
            OrderDetail detail = new OrderDetail();
            detail.setName(cart.getName());
            detail.setImage(cart.getImage());
            detail.setOrderId(order.getId());
            detail.setDishId(cart.getDishId());
            detail.setSetmealId(cart.getSetmealId());
            detail.setDishFlavor(cart.getDishFlavor());
            detail.setNumber(cart.getNumber());
            detail.setAmount(cart.getAmount());
            orderDetailMapper.insert(detail);
        }

        // 5. 清空购物车
        shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));

        return new OrderSubmitVO(order.getId(), order.getNumber(), order.getAmount(), order.getOrderTime());
    }

    /**
     * 支付（幂等核心）
     * <p>
     * 真实场景：微信支付成功后，微信服务器会异步回调通知我们；但网络可能重试，
     * 同一笔订单可能收到多次回调。如果不做幂等，第二次回调会重复记账/重复发货。
     * <p>
     * 幂等方案：先查订单，若 payStatus 已是「已支付」，直接返回，不再重复处理。
     * 数据库层面 orders.number 唯一索引兜底，防重复下单。
     */
    @Override
    public String payment(OrdersPaymentDTO ordersPaymentDTO) {
        Orders order = orderMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getNumber, ordersPaymentDTO.getOrderNumber()));
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 幂等：已支付则直接返回，不做任何重复操作
        if (PayStatusConstant.PAID.equals(order.getPayStatus())) {
            return "duplicate";
        }

        // 模拟支付成功（真实场景：调微信统一下单 + 验签回调）
        order.setPayStatus(PayStatusConstant.PAID);
        order.setStatus(OrderStatusConstant.TO_BE_CONFIRMED);
        order.setCheckoutTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 支付成功后，通过 WebSocket 向商家端推送来单提醒
        webSocketServer.sendToType("1",
                "{\"type\":1,\"orderId\":" + order.getId() + ",\"content\":\"订单号 " + order.getNumber() + "\"}");

        return "success";
    }

    @Override
    public PageResult historyOrders(int page, int pageSize, Integer status) {
        Long userId = BaseContext.getCurrentId();
        Page<Orders> p = new Page<>(page, pageSize);
        orderMapper.selectPage(p, Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getUserId, userId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getOrderTime));
        return new PageResult(p.getTotal(), p.getRecords());
    }

    @Override
    public OrderVO orderDetail(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setOrderDetailList(orderDetailMapper.selectList(
                Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, id)));
        return vo;
    }

    @Override
    public void cancel(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有待付款、待接单状态可取消
        if (!(OrderStatusConstant.PENDING_PAYMENT.equals(order.getStatus())
                || OrderStatusConstant.TO_BE_CONFIRMED.equals(order.getStatus()))) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }
        order.setStatus(OrderStatusConstant.CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason("用户取消");
        orderMapper.updateById(order);
    }

    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> details = orderDetailMapper.selectList(
                Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, id));
        if (CollectionUtils.isEmpty(details)) {
            return;
        }
        // 重新加回购物车（复用购物车逻辑：同商品合并数量）
        for (OrderDetail detail : details) {
            ShoppingCart cart = new ShoppingCart();
            cart.setUserId(userId);
            cart.setName(detail.getName());
            cart.setImage(detail.getImage());
            cart.setDishId(detail.getDishId());
            cart.setSetmealId(detail.getSetmealId());
            cart.setDishFlavor(detail.getDishFlavor());
            cart.setNumber(detail.getNumber());
            cart.setAmount(detail.getAmount());
            cart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(cart);
        }
    }

    @Override
    public void reminder(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 催单推送给商家端（type=2）
        webSocketServer.sendToType("2",
                "{\"type\":2,\"orderId\":" + order.getId() + ",\"content\":\"订单号 " + order.getNumber() + "\"}");
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO dto) {
        Page<Orders> page = new Page<>(dto.getPage(), dto.getPageSize());
        orderMapper.selectPage(page, Wrappers.<Orders>lambdaQuery()
                .eq(dto.getStatus() != null, Orders::getStatus, dto.getStatus())
                .like(dto.getNumber() != null && !dto.getNumber().isEmpty(), Orders::getNumber, dto.getNumber())
                .like(dto.getPhone() != null && !dto.getPhone().isEmpty(), Orders::getPhone, dto.getPhone())
                .ge(dto.getBeginTime() != null, Orders::getOrderTime, dto.getBeginTime())
                .le(dto.getEndTime() != null, Orders::getOrderTime, dto.getEndTime())
                .orderByDesc(Orders::getOrderTime));
        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    public Map<Integer, Long> statistics() {
        // 按状态分组统计（待接单/待派送/派送中 是商家最关心的）
        List<Orders> all = orderMapper.selectList(null);
        Map<Integer, Long> map = new HashMap<>();
        for (Orders o : all) {
            map.merge(o.getStatus(), 1L, Long::sum);
        }
        return map;
    }

    @Override
    public void confirm(Orders orders) {
        Orders db = orderMapper.selectById(orders.getId());
        if (db == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderStatusConstant.TO_BE_CONFIRMED.equals(db.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(OrderStatusConstant.CONFIRMED);
        orderMapper.updateById(update);
    }

    @Override
    public void rejection(Orders orders) {
        Orders db = orderMapper.selectById(orders.getId());
        if (db == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderStatusConstant.TO_BE_CONFIRMED.equals(db.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 拒单：状态改已取消 + 退款（简化：直接标记退款状态）
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(OrderStatusConstant.CANCELLED);
        update.setRejectionReason(orders.getRejectionReason());
        update.setCancelTime(LocalDateTime.now());
        update.setPayStatus(PayStatusConstant.REFUND);
        orderMapper.updateById(update);
    }

    @Override
    public void adminCancel(Orders orders) {
        Orders db = orderMapper.selectById(orders.getId());
        if (db == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders update = new Orders();
        update.setId(orders.getId());
        update.setStatus(OrderStatusConstant.CANCELLED);
        update.setCancelReason(orders.getCancelReason());
        update.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(update);
    }

    @Override
    public void delivery(Long id) {
        Orders db = orderMapper.selectById(id);
        if (db == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderStatusConstant.CONFIRMED.equals(db.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(OrderStatusConstant.DELIVERY_IN_PROGRESS);
        orderMapper.updateById(update);
    }

    @Override
    public void complete(Long id) {
        Orders db = orderMapper.selectById(id);
        if (db == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderStatusConstant.DELIVERY_IN_PROGRESS.equals(db.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = new Orders();
        update.setId(id);
        update.setStatus(OrderStatusConstant.COMPLETED);
        update.setDeliveryTime(LocalDateTime.now());
        orderMapper.updateById(update);
    }

    private String generateOrderNumber() {
        return LocalDateTime.now().format(NUMBER_FORMATTER) + String.format("%06d", RANDOM.nextInt(1000000));
    }

    private String buildAddress(AddressBook addressBook) {
        return (addressBook.getProvinceName() == null ? "" : addressBook.getProvinceName())
                + (addressBook.getCityName() == null ? "" : addressBook.getCityName())
                + (addressBook.getDistrictName() == null ? "" : addressBook.getDistrictName())
                + (addressBook.getDetail() == null ? "" : addressBook.getDetail());
    }
}
