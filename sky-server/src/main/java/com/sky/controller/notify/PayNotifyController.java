package com.sky.controller.notify;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟微信支付回调
 * <p>
 * 真实场景：微信服务器异步通知商户支付结果，URL 是外网可访问的。
 * 这里用 /notify/**（不在 /admin 与 /user 拦截范围内）模拟外部系统回调。
 * 重复调用同一订单号可验证幂等。
 */
@RestController
@RequestMapping("/notify")
public class PayNotifyController {

    @Autowired
    private OrderService orderService;

    /**
     * 模拟微信支付成功回调
     * POST /notify/wechatPay  {orderNumber: "..."}
     */
    @PostMapping("/wechatPay")
    public Result<String> wechatPayNotify(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        String result = orderService.payment(ordersPaymentDTO);
        // 真实场景这里要返回给微信特定格式（如 SUCCESS/FAIL），这里简化
        return Result.success(result);
    }
}
