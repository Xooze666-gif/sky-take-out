package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 微信登录（模拟 code 换 openid），首次登录自动注册
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
