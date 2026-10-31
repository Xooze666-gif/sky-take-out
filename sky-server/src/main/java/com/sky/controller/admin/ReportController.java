package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.UserReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 管理端-统计报表接口
 */
@RestController
@RequestMapping("/admin/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 营业额统计（按天）
     *
     * @param begin 开始日期（含），格式 yyyy-MM-dd
     * @param end   结束日期（含），格式 yyyy-MM-dd
     */
    @GetMapping("/turnoverStatistics")
    public Result<List<BusinessDataVO>> turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(reportService.turnoverStatistics(begin, end));
    }

    /**
     * 用户统计（按天）
     */
    @GetMapping("/userStatistics")
    public Result<List<UserReportVO>> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(reportService.userStatistics(begin, end));
    }

    /**
     * 订单统计（按天）
     */
    @GetMapping("/orderStatistics")
    public Result<List<OrderReportVO>> orderStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(reportService.orderStatistics(begin, end));
    }

    /**
     * 销量 Top10
     */
    @GetMapping("/salesTop10")
    public Result<List<SalesTop10ReportVO>> salesTop10(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(reportService.salesTop10(begin, end));
    }

    /**
     * 导出营业额报表 Excel（最近 30 天）
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        reportService.exportBusinessData(response);
    }
}
