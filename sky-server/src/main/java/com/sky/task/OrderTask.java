package com.sky.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.constant.MessageConstant;
import com.sky.constant.OrderStatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单定时任务（Spring Task）
 * <p>
 * 两条核心规则：
 * 1. 待付款超时 → 自动取消（默认下单后 15 分钟未支付）
 * 2. 派送中超时 → 自动完成（默认下单后 60 分钟，防骑手漏点"完成"导致订单永久挂起）
 * <p>
 * 设计要点（面试常问）：
 * - <b>条件更新代替"先查后改"</b>：UPDATE 语句的 WHERE 里带上 status = 原状态，
 * 由数据库行锁保证原子性。用户恰好在扫描与更新之间自行取消/支付时，本次 UPDATE
 * 影响行数为 0，自动跳过，不会把已支付订单错改成已取消。
 * - <b>批量上限</b>：每次最多处理 batch-size 条，避免积压几十万单时一次全量更新
 * 造成长事务、锁表、主从延迟。
 * - <b>任务本身幂等</b>：状态已经是目标值就不会再被选中，重复执行无副作用。
 * - <b>单机局限</b>：@Scheduled 在多实例部署时会重复执行（每个实例都跑一遍）。
 * 本项目为单机演示；生产多实例应换 xxl-job / Quartz 集群 / ShedLock 分布式锁。
 */
@Component
public class OrderTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTask.class);

    @Autowired
    private OrderMapper orderMapper;

    /** 待付款超时阈值（分钟），下单后超过该时长仍未支付则自动取消 */
    @Value("${sky.task.cancel-payment.timeout-minutes:15}")
    private int cancelPaymentTimeoutMinutes;

    /** 派送中超时阈值（分钟），超过该时长仍未完成则自动完成 */
    @Value("${sky.task.auto-complete.timeout-minutes:60}")
    private int autoCompleteTimeoutMinutes;

    /** 单次扫描最大处理条数，防止大批量积压时一次全量更新 */
    @Value("${sky.task.batch-size:200}")
    private int batchSize;

    /**
     * 待付款订单超时自动取消
     * <p>
     * 执行频率由 sky.task.cancel-payment.cron 控制，默认每分钟一次。
     * 被取消的订单不改 payStatus（本来就没付过钱），但记录 cancelReason / cancelTime，
     * 便于客服与用户端展示"为什么被取消"。
     */
    @Scheduled(cron = "${sky.task.cancel-payment.cron}")
    public void processPendingPaymentTimeout() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(cancelPaymentTimeoutMinutes);

        List<Orders> candidates = orderMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, OrderStatusConstant.PENDING_PAYMENT)
                .lt(Orders::getOrderTime, deadline)
                .orderByAsc(Orders::getOrderTime)
                .last("limit " + batchSize));

        if (candidates.isEmpty()) {
            return;
        }

        int cancelled = 0;
        for (Orders order : candidates) {
            Orders patch = new Orders();
            patch.setStatus(OrderStatusConstant.CANCELLED);
            patch.setCancelReason(MessageConstant.ORDER_CANCELLED_BY_TIMEOUT);
            patch.setCancelTime(now);

            // 关键：WHERE id = ? AND status = 待付款
            int rows = orderMapper.update(patch, Wrappers.<Orders>lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .eq(Orders::getStatus, OrderStatusConstant.PENDING_PAYMENT));

            if (rows > 0) {
                cancelled++;
                log.info("定时取消待付款订单：orderId={}, number={}, 下单时间={}",
                        order.getId(), order.getNumber(), order.getOrderTime());
            } else {
                // 扫描后、更新前，用户刚好完成支付或自行取消，状态已变化 → 跳过
                log.debug("订单 {} 状态已变更，本次跳过取消（并发保护生效）", order.getNumber());
            }
        }

        log.info("待付款超时扫描完成：命中 {} 单，实际取消 {} 单，阈值 {} 分钟",
                candidates.size(), cancelled, cancelPaymentTimeoutMinutes);
    }

    /**
     * 派送中订单超时自动完成
     * <p>
     * 执行频率由 sky.task.auto-complete.cron 控制，默认每天凌晨 1 点。
     * <p>
     * 判断依据说明：orders 表没有"开始派送时间"字段（delivery_time 语义是送达时间，
     * 在 complete 时才写入），因此这里用 order_time 近似。"下单后 60 分钟还没送达"
     * 对普通外卖是合理的兜底；若要精确到"派送中已 X 分钟"，应在 delivery() 里
     * 记录派送开始时间或新增字段，而不是复用 delivery_time。
     */
    @Scheduled(cron = "${sky.task.auto-complete.cron}")
    public void processDeliveryTimeout() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusMinutes(autoCompleteTimeoutMinutes);

        List<Orders> candidates = orderMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, OrderStatusConstant.DELIVERY_IN_PROGRESS)
                .lt(Orders::getOrderTime, deadline)
                .orderByAsc(Orders::getOrderTime)
                .last("limit " + batchSize));

        if (candidates.isEmpty()) {
            return;
        }

        int completed = 0;
        for (Orders order : candidates) {
            Orders patch = new Orders();
            patch.setStatus(OrderStatusConstant.COMPLETED);
            patch.setDeliveryTime(now);

            int rows = orderMapper.update(patch, Wrappers.<Orders>lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .eq(Orders::getStatus, OrderStatusConstant.DELIVERY_IN_PROGRESS));

            if (rows > 0) {
                completed++;
                log.info("定时完成派送中订单：orderId={}, number={}, 下单时间={}",
                        order.getId(), order.getNumber(), order.getOrderTime());
            } else {
                log.debug("订单 {} 状态已变更，本次跳过自动完成（并发保护生效）", order.getNumber());
            }
        }

        log.info("派送中超时扫描完成：命中 {} 单，实际完成 {} 单，阈值 {} 分钟",
                candidates.size(), completed, autoCompleteTimeoutMinutes);
    }
}
