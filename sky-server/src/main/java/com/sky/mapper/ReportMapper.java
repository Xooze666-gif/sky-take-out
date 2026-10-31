package com.sky.mapper;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.UserReportVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表 Mapper
 * <p>
 * 说明：统计 SQL 属于聚合查询，不适合 BaseMapper 的简单 CRUD，
 * 统一用 XML 手写，聚合逻辑下沉到数据库（更贴近生产做法）。
 */
@Mapper
public interface ReportMapper {

    /**
     * 营业额统计：按天汇总已完成订单的实收金额与订单数
     *
     * @param begin 开始日期（含）
     * @param end   结束日期（含）
     */
    List<BusinessDataVO> turnoverStatistics(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 用户统计：按天统计新增用户数
     *
     * @param begin 开始日期（含）
     * @param end   结束日期（含）
     */
    List<UserReportVO> userStatistics(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 截止某日期的用户累计总数（含该日）
     */
    Integer countUsersByDate(@Param("end") LocalDate end);

    /**
     * 订单统计：按天统计订单总数与有效（已完成）订单数
     *
     * @param begin 开始日期（含）
     * @param end   结束日期（含）
     */
    List<OrderReportVO> orderStatistics(@Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 销量 Top10：按订单明细汇总商品销量并倒序
     *
     * @param begin 开始日期（含）
     * @param end   结束日期（含）
     */
    List<SalesTop10ReportVO> salesTop10(@Param("begin") LocalDate begin, @Param("end") LocalDate end);
}
