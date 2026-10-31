package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工 Mapper
 * <p>
 * 继承 BaseMapper 即获得单表 CRUD + 条件构造器，无需手写 XML。
 * 复杂多表 SQL 仍可在此声明方法并写 XML。
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
