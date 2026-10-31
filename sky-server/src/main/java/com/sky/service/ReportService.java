package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.UserReportVO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表服务
 */
public interface ReportService {

    /**
     * 营业额统计（按天，含完成率/客单价/新增用户补全）
     */
    List<BusinessDataVO> turnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计（按天，含累计总数补全）
     */
    List<UserReportVO> userStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计（按天，含完成率补全）
     */
    List<OrderReportVO> orderStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量 Top10
     */
    List<SalesTop10ReportVO> salesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出营业额报表 Excel（写入 HTTP 响应）
     */
    void exportBusinessData(HttpServletResponse response);
}
