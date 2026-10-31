package com.sky.service.impl;

import com.sky.mapper.ReportMapper;
import com.sky.service.ReportService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.UserReportVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表服务实现
 */
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Override
    public List<BusinessDataVO> turnoverStatistics(LocalDate begin, LocalDate end) {
        List<BusinessDataVO> list = reportMapper.turnoverStatistics(begin, end);

        // 完成率 / 客单价补全：需要每天的订单总数，复用订单统计结果
        Map<String, Integer> totalOrderMap = new HashMap<>();
        for (OrderReportVO orderReport : reportMapper.orderStatistics(begin, end)) {
            totalOrderMap.put(orderReport.getDate().toString(), orderReport.getTotalOrderCount());
        }

        for (BusinessDataVO vo : list) {
            Integer validCount = vo.getValidOrderCount() == null ? 0 : vo.getValidOrderCount();
            Integer totalCount = totalOrderMap.getOrDefault(vo.getDate(), 0);

            // 订单完成率 = 有效订单数 / 总订单数
            if (totalCount != null && totalCount > 0) {
                vo.setOrderCompletionRate(round(validCount * 1.0 / totalCount));
            } else {
                vo.setOrderCompletionRate(0.0);
            }

            // 平均客单价 = 营业额 / 有效订单数
            Double turnover = vo.getTurnover() == null ? 0.0 : vo.getTurnover();
            if (validCount > 0) {
                vo.setUnitPrice(round(turnover / validCount));
            } else {
                vo.setUnitPrice(0.0);
            }
        }
        return list;
    }

    @Override
    public List<UserReportVO> userStatistics(LocalDate begin, LocalDate end) {
        List<UserReportVO> list = reportMapper.userStatistics(begin, end);

        // 累计用户数补全：区间起点前的历史累计 + 区间内逐日累加
        int cumulative = reportMapper.countUsersByDate(begin.minusDays(1));
        for (UserReportVO vo : list) {
            cumulative += vo.getNewUserCount();
            vo.setTotalUserCount(cumulative);
        }
        return list;
    }

    @Override
    public List<OrderReportVO> orderStatistics(LocalDate begin, LocalDate end) {
        List<OrderReportVO> list = reportMapper.orderStatistics(begin, end);
        for (OrderReportVO vo : list) {
            Integer total = vo.getTotalOrderCount() == null ? 0 : vo.getTotalOrderCount();
            Integer valid = vo.getValidOrderCount() == null ? 0 : vo.getValidOrderCount();
            if (total > 0) {
                vo.setOrderCompletionRate(round(valid * 1.0 / total));
            } else {
                vo.setOrderCompletionRate(0.0);
            }
        }
        return list;
    }

    @Override
    public List<SalesTop10ReportVO> salesTop10(LocalDate begin, LocalDate end) {
        return reportMapper.salesTop10(begin, end);
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 导出最近 30 天的营业额报表
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(29);
        List<BusinessDataVO> list = turnoverStatistics(begin, end);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("营业额报表");
            // 列宽
            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 18 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 18 * 256);
            sheet.setColumnWidth(4, 18 * 256);

            // 表头样式
            CellStyle headStyle = workbook.createCellStyle();
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);

            // 表头
            String[] headers = {"日期", "营业额", "有效订单数", "订单完成率", "平均客单价"};
            Row headRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headStyle);
            }

            // 数据行
            int rowIndex = 1;
            for (BusinessDataVO vo : list) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(vo.getDate());
                row.createCell(1).setCellValue(vo.getTurnover() == null ? 0.0 : vo.getTurnover());
                row.createCell(2).setCellValue(vo.getValidOrderCount() == null ? 0 : vo.getValidOrderCount());
                row.createCell(3).setCellValue(vo.getOrderCompletionRate() == null ? 0.0 : vo.getOrderCompletionRate());
                row.createCell(4).setCellValue(vo.getUnitPrice() == null ? 0.0 : vo.getUnitPrice());
            }

            // 设置响应头，触发浏览器下载
            String fileName = "营业额报表_" + begin + "_至_" + end + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));

            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Excel 导出失败", e);
        }
    }

    /**
     * 保留两位小数
     */
    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
