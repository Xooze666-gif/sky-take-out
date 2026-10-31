-- ============================================================
-- 苍穹外卖 (sky-take-out) 数据库脚本
-- MySQL 8.0+  字符集 utf8mb4
-- 说明：11 张表，含完整索引与种子数据
-- 执行方式：mysql -uroot -p < sky_take_out.sql
-- ============================================================

DROP DATABASE IF EXISTS sky_take_out;
CREATE DATABASE sky_take_out DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE sky_take_out;

-- ------------------------------------------------------------
-- 1. 员工表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS employee;
CREATE TABLE employee
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32) NOT NULL COMMENT '姓名',
    username    VARCHAR(32) NOT NULL COMMENT '用户名',
    password    VARCHAR(64) NOT NULL COMMENT '密码(MD5)',
    phone       VARCHAR(11) NOT NULL COMMENT '手机号',
    sex         VARCHAR(2)  NOT NULL COMMENT '性别',
    id_number   VARCHAR(18) NOT NULL COMMENT '身份证号',
    status      INT         NOT NULL DEFAULT 1 COMMENT '状态 0:禁用 1:启用',
    create_time DATETIME             DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME             DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT               DEFAULT NULL COMMENT '创建人',
    update_user BIGINT               DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    UNIQUE KEY idx_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='员工信息';

-- ------------------------------------------------------------
-- 2. 分类表（菜品分类 / 套餐分类）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS category;
CREATE TABLE category
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    type        INT                  DEFAULT NULL COMMENT '类型 1菜品分类 2套餐分类',
    name        VARCHAR(32) NOT NULL COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    status      INT                  DEFAULT NULL COMMENT '状态 0:禁用 1:启用',
    create_time DATETIME             DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME             DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT               DEFAULT NULL COMMENT '创建人',
    update_user BIGINT               DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    UNIQUE KEY idx_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品及套餐分类';

-- ------------------------------------------------------------
-- 3. 菜品表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dish;
CREATE TABLE dish
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)    NOT NULL COMMENT '菜品名称',
    category_id BIGINT         NOT NULL COMMENT '菜品分类id',
    price       DECIMAL(10, 2)          DEFAULT NULL COMMENT '菜品价格',
    image       VARCHAR(255)            DEFAULT NULL COMMENT '图片',
    description VARCHAR(255)            DEFAULT NULL COMMENT '描述信息',
    status      INT                     DEFAULT 1 COMMENT '0 停售 1 起售',
    create_time DATETIME                DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME                DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT                  DEFAULT NULL COMMENT '创建人',
    update_user BIGINT                  DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    UNIQUE KEY idx_dish_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品';

-- ------------------------------------------------------------
-- 4. 菜品口味表（一个菜品多个口味，如 辣度/温度/忌口）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS dish_flavor;
CREATE TABLE dish_flavor
(
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    dish_id BIGINT NOT NULL COMMENT '菜品',
    name    VARCHAR(32)  DEFAULT NULL COMMENT '口味名称',
    value   VARCHAR(255) DEFAULT NULL COMMENT '口味数据list',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品口味关系表';

-- ------------------------------------------------------------
-- 5. 套餐表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS setmeal;
CREATE TABLE setmeal
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT         NOT NULL COMMENT '套餐分类id',
    name        VARCHAR(32)    NOT NULL COMMENT '套餐名称',
    price       DECIMAL(10, 2) NOT NULL COMMENT '套餐价格',
    status      INT                     DEFAULT 1 COMMENT '状态 0:停用 1:启用',
    description VARCHAR(255)            DEFAULT NULL COMMENT '描述信息',
    image       VARCHAR(255)            DEFAULT NULL COMMENT '图片',
    create_time DATETIME                DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME                DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT                  DEFAULT NULL COMMENT '创建人',
    update_user BIGINT                  DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    UNIQUE KEY idx_setmeal_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='套餐';

-- ------------------------------------------------------------
-- 6. 套餐菜品关系表（多对多）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS setmeal_dish;
CREATE TABLE setmeal_dish
(
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    setmeal_id BIGINT                  DEFAULT NULL COMMENT '套餐id',
    dish_id    BIGINT                  DEFAULT NULL COMMENT '菜品id',
    name       VARCHAR(32)             DEFAULT NULL COMMENT '菜品名称(冗余，防菜品改名影响历史套餐)',
    price      DECIMAL(10, 2)          DEFAULT NULL COMMENT '菜品单价(冗余快照)',
    copies     INT                     DEFAULT NULL COMMENT '菜品份数',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='套餐菜品关系';

-- ------------------------------------------------------------
-- 7. C 端用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid      VARCHAR(45) NOT NULL COMMENT '微信用户唯一标识',
    name        VARCHAR(32)          DEFAULT NULL COMMENT '姓名',
    phone       VARCHAR(11)          DEFAULT NULL COMMENT '手机号',
    sex         VARCHAR(2)           DEFAULT NULL COMMENT '性别',
    id_number   VARCHAR(18)          DEFAULT NULL COMMENT '身份证号',
    avatar      VARCHAR(500)         DEFAULT NULL COMMENT '头像',
    create_time DATETIME             DEFAULT NULL COMMENT '注册时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户信息';

-- ------------------------------------------------------------
-- 8. 地址簿
-- ------------------------------------------------------------
DROP TABLE IF EXISTS address_book;
CREATE TABLE address_book
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '用户id',
    consignee      VARCHAR(50)           DEFAULT NULL COMMENT '收货人',
    sex            VARCHAR(2)            DEFAULT NULL COMMENT '性别',
    phone          VARCHAR(11)  NOT NULL COMMENT '手机号',
    province_code  VARCHAR(12)           DEFAULT NULL COMMENT '省级区划编号',
    province_name  VARCHAR(32)           DEFAULT NULL COMMENT '省级名称',
    city_code      VARCHAR(12)           DEFAULT NULL COMMENT '市级区划编号',
    city_name      VARCHAR(32)           DEFAULT NULL COMMENT '市级名称',
    district_code  VARCHAR(12)           DEFAULT NULL COMMENT '区级区划编号',
    district_name  VARCHAR(32)           DEFAULT NULL COMMENT '区级名称',
    detail         VARCHAR(200)          DEFAULT NULL COMMENT '详细地址',
    label          VARCHAR(100)          DEFAULT NULL COMMENT '标签',
    is_default     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '默认地址 0否 1是',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='地址簿';

-- ------------------------------------------------------------
-- 9. 购物车
-- ------------------------------------------------------------
DROP TABLE IF EXISTS shopping_cart;
CREATE TABLE shopping_cart
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)             DEFAULT NULL COMMENT '商品名称',
    image       VARCHAR(255)            DEFAULT NULL COMMENT '图片',
    user_id     BIGINT         NOT NULL COMMENT '用户id',
    dish_id     BIGINT                  DEFAULT NULL COMMENT '菜品id',
    setmeal_id  BIGINT                  DEFAULT NULL COMMENT '套餐id',
    dish_flavor VARCHAR(50)             DEFAULT NULL COMMENT '口味',
    number      INT            NOT NULL DEFAULT 1 COMMENT '数量',
    amount      DECIMAL(10, 2) NOT NULL COMMENT '单价',
    create_time DATETIME                DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='购物车';

-- ------------------------------------------------------------
-- 10. 订单表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS orders;
CREATE TABLE orders
(
    id                      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    number                  VARCHAR(50)             DEFAULT NULL COMMENT '订单号(幂等/对账关键字段)',
    status                  INT            NOT NULL DEFAULT 1 COMMENT '1待付款 2待接单 3已接单 4派送中 5已完成 6已取消',
    user_id                 BIGINT         NOT NULL COMMENT '下单用户',
    address_book_id         BIGINT         NOT NULL COMMENT '地址id',
    order_time              DATETIME       NOT NULL COMMENT '下单时间',
    checkout_time           DATETIME                DEFAULT NULL COMMENT '结账时间',
    pay_method              INT            NOT NULL COMMENT '支付方式 1微信 2支付宝',
    pay_status              TINYINT        NOT NULL DEFAULT 0 COMMENT '支付状态 0未支付 1已支付 2退款',
    amount                  DECIMAL(10, 2) NOT NULL COMMENT '实收金额',
    remark                  VARCHAR(100)            DEFAULT NULL COMMENT '备注',
    phone                   VARCHAR(11)             DEFAULT NULL COMMENT '手机号',
    address                 VARCHAR(255)            DEFAULT NULL COMMENT '地址',
    consignee               VARCHAR(32)             DEFAULT NULL COMMENT '收货人',
    cancel_reason           VARCHAR(255)            DEFAULT NULL COMMENT '订单取消原因',
    rejection_reason        VARCHAR(255)            DEFAULT NULL COMMENT '拒单原因',
    cancel_time             DATETIME                DEFAULT NULL COMMENT '订单取消时间',
    estimated_delivery_time DATETIME                DEFAULT NULL COMMENT '预计送达时间',
    delivery_status         TINYINT                 DEFAULT 1 COMMENT '配送状态 1立即送出 0选择具体时间',
    delivery_time           DATETIME                DEFAULT NULL COMMENT '送达时间',
    pack_amount             INT                     DEFAULT NULL COMMENT '打包费',
    tableware_number        INT                     DEFAULT NULL COMMENT '餐具数量',
    tableware_status        TINYINT                 DEFAULT 1 COMMENT '餐具数量状态 1按餐量提供 0选择具体数量',
    PRIMARY KEY (id),
    UNIQUE KEY idx_order_number (number),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单';

-- ------------------------------------------------------------
-- 11. 订单明细
-- ------------------------------------------------------------
DROP TABLE IF EXISTS order_detail;
CREATE TABLE order_detail
(
    id          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)             DEFAULT NULL COMMENT '商品名称',
    image       VARCHAR(255)            DEFAULT NULL COMMENT '商品图片',
    order_id    BIGINT         NOT NULL COMMENT '订单id',
    dish_id     BIGINT                  DEFAULT NULL COMMENT '菜品id',
    setmeal_id  BIGINT                  DEFAULT NULL COMMENT '套餐id',
    dish_flavor VARCHAR(50)             DEFAULT NULL COMMENT '口味',
    number      INT            NOT NULL DEFAULT 1 COMMENT '数量',
    amount      DECIMAL(10, 2) NOT NULL COMMENT '单价',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细';

-- ============================================================
-- 种子数据
-- ============================================================

-- 员工：admin / 123456（MD5）
INSERT INTO employee (id, name, username, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user)
VALUES (1, '管理员', 'admin', 'e10adc3949ba59abbe56e057f20f883e', '13800000000', '1', '110101199001010001', 1, NOW(), NOW(), 1, 1),
       (2, '张三', 'zhangsan', 'e10adc3949ba59abbe56e057f20f883e', '13800000001', '1', '110101199001010002', 1, NOW(), NOW(), 1, 1),
       (3, '李四', 'lisi', 'e10adc3949ba59abbe56e057f20f883e', '13800000002', '0', '110101199001010003', 0, NOW(), NOW(), 1, 1);

-- 分类：菜品分类(type=1) / 套餐分类(type=2)
INSERT INTO category (id, type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES (1, 1, '酒水饮料', 10, 1, NOW(), NOW(), 1, 1),
       (2, 1, '传统主食', 20, 1, NOW(), NOW(), 1, 1),
       (3, 1, '川湘菜', 30, 1, NOW(), NOW(), 1, 1),
       (4, 1, '江湖菜', 40, 1, NOW(), NOW(), 1, 1),
       (5, 1, '素菜', 50, 1, NOW(), NOW(), 1, 1),
       (6, 2, '人气套餐', 60, 1, NOW(), NOW(), 1, 1),
       (7, 2, '商务套餐', 70, 1, NOW(), NOW(), 1, 1);

-- 菜品（12 条，便于验证每页 10 条的分页）
INSERT INTO dish (id, name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
VALUES (1, '王老吉', 1, 6.00, '/images/wlj.png', '经典凉茶', 1, NOW(), NOW(), 1, 1),
       (2, '北冰洋', 1, 5.00, '/images/bby.png', '老北京汽水', 1, NOW(), NOW(), 1, 1),
       (3, '米饭', 2, 2.00, '/images/mifan.png', '东北大米', 1, NOW(), NOW(), 1, 1),
       (4, '手工水饺', 2, 22.00, '/images/shuijiao.png', '猪肉大葱馅', 1, NOW(), NOW(), 1, 1),
       (5, '麻婆豆腐', 3, 18.00, '/images/mapo.png', '麻辣鲜香', 1, NOW(), NOW(), 1, 1),
       (6, '水煮鱼', 3, 68.00, '/images/shuizhuyu.png', '草鱼现杀', 1, NOW(), NOW(), 1, 1),
       (7, '宫保鸡丁', 3, 38.00, '/images/gongbao.png', '酸甜微辣', 1, NOW(), NOW(), 1, 1),
       (8, '辣子鸡', 4, 48.00, '/images/laziji.png', '川味经典', 1, NOW(), NOW(), 1, 1),
       (9, '酸菜鱼', 4, 58.00, '/images/suancaiyu.png', '开胃下饭', 1, NOW(), NOW(), 1, 1),
       (10, '清炒时蔬', 5, 16.00, '/images/qingchao.png', '当季蔬菜', 1, NOW(), NOW(), 1, 1),
       (11, '手撕包菜', 5, 18.00, '/images/shouSi.png', '干锅风味', 0, NOW(), NOW(), 1, 1),
       (12, '蒜蓉西兰花', 5, 20.00, '/images/xilanhua.png', '清淡爽口', 1, NOW(), NOW(), 1, 1);

-- 菜品口味（一对多）
INSERT INTO dish_flavor (id, dish_id, name, value)
VALUES (1, 6, '辣度', '["不辣","微辣","中辣","重辣"]'),
       (2, 6, '忌口', '["不要葱","不要蒜","不要香菜"]'),
       (3, 7, '辣度', '["不辣","微辣","中辣"]'),
       (4, 8, '辣度', '["微辣","中辣","重辣"]'),
       (5, 9, '辣度', '["不辣","微辣","中辣"]'),
       (6, 4, '忌口', '["不要葱","不要香菜"]');

-- 套餐
INSERT INTO setmeal (id, category_id, name, price, status, description, image, create_time, update_time, create_user, update_user)
VALUES (1, 6, '人气王套餐', 88.00, 1, '水煮鱼+米饭+王老吉', '/images/rq1.png', NOW(), NOW(), 1, 1),
       (2, 6, '经典川味套餐', 78.00, 1, '麻婆豆腐+宫保鸡丁+米饭', '/images/rq2.png', NOW(), NOW(), 1, 1),
       (3, 7, '商务单人套餐', 58.00, 1, '辣子鸡+清炒时蔬+米饭', '/images/sw1.png', NOW(), NOW(), 1, 1),
       (4, 7, '双人商务套餐', 128.00, 0, '酸菜鱼+手撕包菜+米饭x2', '/images/sw2.png', NOW(), NOW(), 1, 1);

-- 套餐-菜品关系
INSERT INTO setmeal_dish (id, setmeal_id, dish_id, name, price, copies)
VALUES (1, 1, 6, '水煮鱼', 68.00, 1),
       (2, 1, 3, '米饭', 2.00, 2),
       (3, 1, 1, '王老吉', 6.00, 1),
       (4, 2, 5, '麻婆豆腐', 18.00, 1),
       (5, 2, 7, '宫保鸡丁', 38.00, 1),
       (6, 2, 3, '米饭', 2.00, 1),
       (7, 3, 8, '辣子鸡', 48.00, 1),
       (8, 3, 10, '清炒时蔬', 16.00, 1),
       (9, 3, 3, '米饭', 2.00, 1),
       (10, 4, 9, '酸菜鱼', 58.00, 1),
       (11, 4, 11, '手撕包菜', 18.00, 1),
       (12, 4, 3, '米饭', 2.00, 2);

-- C 端用户
INSERT INTO `user` (id, openid, name, phone, sex, avatar, create_time)
VALUES (1, 'mock_openid_001', '小张', '13900000001', '1', '/images/u1.png', NOW()),
       (2, 'mock_openid_002', '小李', '13900000002', '0', '/images/u2.png', NOW()),
       (3, 'mock_openid_003', '小王', '13900000003', '1', '/images/u3.png', NOW());

-- 地址簿
INSERT INTO address_book (id, user_id, consignee, sex, phone, province_name, city_name, district_name, detail, label, is_default)
VALUES (1, 1, '小张', '1', '13900000001', '北京市', '北京市', '海淀区', '中关村大街 1 号', '公司', 1),
       (2, 2, '小李', '0', '13900000002', '北京市', '北京市', '朝阳区', '建国路 88 号', '家', 1);

-- 演示订单
INSERT INTO orders (id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount, remark, phone, address, consignee, estimated_delivery_time, pack_amount, tableware_number, tableware_status)
VALUES (1, '2026101700000001', 5, 1, 1, NOW(), NOW(), 1, 1, 88.00, '少放辣', '13900000001', '北京市海淀区中关村大街 1 号', '小张', NOW(), 2, 2, 1),
       (2, '2026101700000002', 2, 2, 2, NOW(), NULL, 1, 1, 78.00, NULL, '13900000002', '北京市朝阳区建国路 88 号', '小李', NOW(), 2, 1, 1);

INSERT INTO order_detail (id, name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
VALUES (1, '人气王套餐', '/images/rq1.png', 1, NULL, 1, NULL, 1, 88.00),
       (2, '经典川味套餐', '/images/rq2.png', 2, NULL, 2, NULL, 1, 78.00);

-- ============================================================
-- 校验
-- ============================================================
SELECT '员工' AS 表名, COUNT(*) AS 记录数 FROM employee
UNION ALL SELECT '分类', COUNT(*) FROM category
UNION ALL SELECT '菜品', COUNT(*) FROM dish
UNION ALL SELECT '口味', COUNT(*) FROM dish_flavor
UNION ALL SELECT '套餐', COUNT(*) FROM setmeal
UNION ALL SELECT '套餐菜品', COUNT(*) FROM setmeal_dish
UNION ALL SELECT '用户', COUNT(*) FROM `user`
UNION ALL SELECT '地址', COUNT(*) FROM address_book
UNION ALL SELECT '订单', COUNT(*) FROM orders
UNION ALL SELECT '订单明细', COUNT(*) FROM order_detail;
