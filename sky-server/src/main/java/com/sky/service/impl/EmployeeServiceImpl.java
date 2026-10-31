package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.BaseException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private EmployeeMapper employeeMapper;

    @Override
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new AccountNotFoundException("用户名或密码为空");
        }

        Employee employee = employeeMapper.selectOne(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getUsername, username));

        if (employee == null) {
            throw new AccountNotFoundException();
        }

        String md5 = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        if (!md5.equals(employee.getPassword())) {
            throw new PasswordErrorException();
        }

        if (StatusConstant.DISABLE.equals(employee.getStatus())) {
            throw new AccountLockedException();
        }

        return employee;
    }

    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        // 新增员工默认启用
        employee.setStatus(StatusConstant.ENABLE);
        // 默认密码 123456，入库前 MD5
        employee.setPassword(DigestUtils.md5DigestAsHex(DEFAULT_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        // create_time / create_user / update_time / update_user 由 MetaObjectHandler 自动填充
        employeeMapper.insert(employee);
    }

    @Override
    public PageResult pageQuery(EmployeePageQueryDTO dto) {
        // 分页构造
        Page<Employee> page = new Page<>(dto.getPage(), dto.getPageSize());

        // 查询条件：姓名模糊 + 按创建时间倒序
        LambdaQueryWrapper<Employee> wrapper = Wrappers.<Employee>lambdaQuery()
                .like(StringUtils.hasText(dto.getName()), Employee::getName, dto.getName())
                .orderByDesc(Employee::getCreateTime);

        employeeMapper.selectPage(page, wrapper);

        return new PageResult(page.getTotal(), page.getRecords());
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        // 用 update 而非先查再改：一次 SQL 完成，且能防止「查出来已不存在」的并发问题
        Employee employee = new Employee();
        employee.setId(id);
        employee.setStatus(status);
        // update_time / update_user 由 MetaObjectHandler 自动填充
        int rows = employeeMapper.updateById(employee);
        if (rows == 0) {
            throw new BaseException("员工不存在");
        }
    }

    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new AccountNotFoundException();
        }
        // 密码敏感，回显前抹掉
        employee.setPassword(null);
        return employee;
    }

    @Override
    public void update(EmployeeDTO employeeDTO) {
        // 修改不允许改用户名（username 是唯一键，前端也不传）
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setUsername(null);
        // 防止把密码字段误置空
        employee.setPassword(null);
        employee.setStatus(null);

        int rows = employeeMapper.updateById(employee);
        if (rows == 0) {
            throw new BaseException("员工不存在");
        }
    }
}
