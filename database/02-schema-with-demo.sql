-- MyPetStore实验一：表结构与演示数据（独立完整脚本）
-- 适用：MySQL 8.0.16+ / 8.4，InnoDB，utf8mb4。
-- 与01-schema.sql二选一，不按编号依次执行；此文件已经包含完整表结构。
-- 在空的MyPetStore中首次执行；不DROP、不清空、不覆盖已有表。
-- 已有同名表时会报错，请检查目标数据库，不要使用mysql --force跳过错误。
-- 本文件不创建数据库账号，不配置应用密码，也不会由应用启动时自动执行。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+08:00';

CREATE DATABASE IF NOT EXISTS MyPetStore
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE MyPetStore;

-- ===== 七张表的统一结构开始 =====

-- 1. 教学账号：登录身份与普通用户/管理员角色。
CREATE TABLE store_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键，Java Long',
    username VARCHAR(50) NOT NULL COMMENT '登录名，创建后不修改，不区分大小写唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt散列，不保存明文密码',
    display_name VARCHAR(50) NOT NULL COMMENT '显示名称',
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CUSTOMER或ADMIN',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED',
    email VARCHAR(100) NULL,
    phone VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT ck_user_username CHECK (CHAR_LENGTH(TRIM(username)) > 0),
    CONSTRAINT ck_user_password CHECK (CHAR_LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT ck_user_display_name CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_user_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户及管理员账号';

-- 2. 两级分类：parent_id为空表示一级；二级必须指向一级，由Service校验。
-- parent_key是生成列：插入/更新实体时不要写入它。
CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL COMMENT '全局唯一编码，创建后不修改',
    parent_id BIGINT NULL COMMENT '一级为空，二级指向一级，创建后不修改',
    parent_key BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (code),
    UNIQUE KEY uk_category_sibling_name (parent_key, name),
    KEY idx_category_parent_sort (parent_id, sort_order, id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_category_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_category_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定两级商品分类';

-- 3. 一个商品对应一个可销售SKU，不做多规格；下架代替删除。
CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(40) NOT NULL COMMENT '唯一销售编码，创建后不修改',
    category_id BIGINT NOT NULL COMMENT '必须归属二级分类，由Service校验',
    name VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NULL,
    description TEXT NULL COMMENT '纯文本详情，模板转义展示',
    cover_image VARCHAR(255) NOT NULL DEFAULT 'images/logo.png' COMMENT '站内静态资源相对路径',
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0 COMMENT '可销售库存：下单扣减，未支付取消恢复',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '所有商品写操作显式递增，防旧值覆盖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku (sku),
    KEY idx_product_category_status_sort (category_id, status, sort_order, id),
    KEY idx_product_status_sort (status, sort_order, id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_product_sku CHECK (CHAR_LENGTH(TRIM(sku)) > 0),
    CONSTRAINT ck_product_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_product_cover CHECK (CHAR_LENGTH(TRIM(cover_image)) > 0),
    CONSTRAINT ck_product_price CHECK (price > 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ON_SALE')),
    CONSTRAINT ck_product_sort CHECK (sort_order >= 0),
    CONSTRAINT ck_product_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品及可销售库存';

-- 4. 图片相册与封面分开；多个商品可复用相同的教学图片。
CREATE TABLE product_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    alt_text VARCHAR(120) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_image_product_sort (product_id, sort_order, id),
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_image_path CHECK (CHAR_LENGTH(TRIM(image_path)) > 0),
    CONSTRAINT ck_image_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品详情相册';

-- 5. 登录用户的持久购物车：价格不存这里；购物车不占库存。
CREATE TABLE cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    KEY idx_cart_product (product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES store_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_cart_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_cart_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按用户持久化的购物车条目';

-- 6. 订单主表：模拟付款成功即PAID（待发货），没有真实支付渠道或退款。
CREATE TABLE sales_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL COMMENT '服务端生成的唯一展示订单号',
    user_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING_PAYMENT',
    total_amount DECIMAL(12,2) NOT NULL,
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL COMMENT '下单时完整收货地址快照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    payment_method VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '成功后固定MOCK',
    payment_no VARCHAR(40) NULL COMMENT '模拟成功凭证，未支付为空，可空唯一',
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    shipped_at DATETIME NULL,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_payment_no (payment_no),
    KEY idx_order_user_created (user_id, created_at, id),
    KEY idx_order_status_created (status, created_at, id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES store_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_no CHECK (CHAR_LENGTH(TRIM(order_no)) > 0),
    CONSTRAINT ck_order_total CHECK (total_amount > 0),
    CONSTRAINT ck_order_receiver CHECK (
        CHAR_LENGTH(TRIM(receiver_name)) > 0 AND
        CHAR_LENGTH(TRIM(receiver_phone)) > 0 AND
        CHAR_LENGTH(TRIM(receiver_address)) > 0
    ),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_order_payment_info CHECK (
        (status IN ('PENDING_PAYMENT', 'CANCELLED')
            AND payment_method IS NULL AND payment_no IS NULL AND paid_at IS NULL)
        OR
        (status IN ('PAID', 'SHIPPED', 'COMPLETED')
            AND payment_method IS NOT NULL AND payment_method = 'MOCK'
            AND payment_no IS NOT NULL AND CHAR_LENGTH(TRIM(payment_no)) > 0
            AND paid_at IS NOT NULL)
    ),
    CONSTRAINT ck_order_cancel_time CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    ),
    CONSTRAINT ck_order_ship_time CHECK (
        (status IN ('SHIPPED', 'COMPLETED') AND shipped_at IS NOT NULL)
        OR (status NOT IN ('SHIPPED', 'COMPLETED') AND shipped_at IS NULL)
    ),
    CONSTRAINT ck_order_complete_time CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_order_time_sequence CHECK (
        (paid_at IS NULL OR paid_at >= created_at) AND
        (cancelled_at IS NULL OR cancelled_at >= created_at) AND
        (shipped_at IS NULL OR shipped_at >= paid_at) AND
        (completed_at IS NULL OR completed_at >= shipped_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单及收货、模拟支付信息';

-- 7. 订单明细：保留成交时快照，不随商品后续变化而更新。
CREATE TABLE sales_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(40) NOT NULL,
    product_name_snapshot VARCHAR(120) NOT NULL,
    cover_image_snapshot VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_item_product (order_id, product_id),
    KEY idx_order_item_product (product_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES sales_order (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_order_item_sku CHECK (CHAR_LENGTH(TRIM(sku_snapshot)) > 0),
    CONSTRAINT ck_order_item_name CHECK (CHAR_LENGTH(TRIM(product_name_snapshot)) > 0),
    CONSTRAINT ck_order_item_cover CHECK (CHAR_LENGTH(TRIM(cover_image_snapshot)) > 0),
    CONSTRAINT ck_order_item_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_item_amount CHECK (line_amount > 0 AND line_amount = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品快照与金额明细';

-- ===== 七张表的统一结构结束 =====

-- Service仍需保证：只允许二级分类归属商品、禁止三级/循环分类、角色与所有权、
-- 购物车最多20种、用户行锁、库存和version更新、状态迁移、订单合计等于明细合计。
-- 下单的订单/明细/扣库存/删除结算条目须同一事务，不由触发器自动完成。

-- ===== 演示数据开始 =====
-- 教学演示密码统一为 PetStore123!；BCrypt cost=10，每个账号独立盐。
-- 仅为本地教学账号，不可作为真实部署凭据。不要在SQL中执行MD5/SHA2替代密码校验。
-- 密码校验约定：BCryptPasswordEncoder.matches(rawPassword, password_hash)。
-- 以下写入在一个事务中提交；DDL由MySQL独立提交，不宣称建库建表可随此事务回滚。
START TRANSACTION;

INSERT INTO store_user
    (id, username, password_hash, display_name, role, status, email, phone)
VALUES
    (1, 'alice', '$2a$10$L2rlMCJruRZDIL2pXB.BBOm7LiOuhGNmxWJHJH2n2QQ.BS5JLlsOe', '林同学', 'CUSTOMER', 'ENABLED', 'alice@example.test', NULL),
    (2, 'bob', '$2a$10$5f6JeeY8hi.NpV0nS7IbXep9siN.7Df4P8PLQVfhmZdq7EaLl80pm', '陈同学', 'CUSTOMER', 'ENABLED', 'bob@example.test', NULL),
    (3, 'disabled', '$2a$10$nyqfhZXjqmbrVaijpcWHVOSo7nwuOgFLyFkkp9EM3wf5y0yiao.lS', '禁用教学账号', 'CUSTOMER', 'DISABLED', 'disabled@example.test', NULL),
    (4, 'admin', '$2a$10$TWF0IW1UuBapXm2v.rwh2u9CemHHGhrjybt9CLxmNcXG1t6boGrtK', '商城管理员', 'ADMIN', 'ENABLED', 'admin@example.test', NULL);

-- 一级分类先插入，二级分类后插入；不同一级下允许同名二级。
INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
    (1, 'DOGS', NULL, '犬类', 10),
    (2, 'CATS', NULL, '猫类', 20),
    (3, 'FISH', NULL, '水族', 30),
    (4, 'BIRDS', NULL, '鸟类', 40),
    (5, 'REPTILES', NULL, '爬宠', 50);

INSERT INTO category (id, code, parent_id, name, sort_order) VALUES
    (101, 'DOGS_PETS', 1, '宠物', 10),
    (102, 'DOGS_SUPPLIES', 1, '用品', 20),
    (201, 'CATS_PETS', 2, '宠物', 10),
    (202, 'CATS_SUPPLIES', 2, '用品', 20),
    (301, 'FISH_PETS', 3, '宠物', 10),
    (302, 'FISH_SUPPLIES', 3, '用品', 20),
    (401, 'BIRDS_PETS', 4, '宠物', 10),
    (402, 'BIRDS_SUPPLIES', 4, '用品', 20),
    (501, 'REPTILES_PETS', 5, '宠物', 10),
    (502, 'REPTILES_SUPPLIES', 5, '用品', 20);

-- 原有20个商品保留：18个上架（其中1个售罄）、2个下架；后文另追加20个用品。
-- 图片是原型素材的教学复用，不表示同图商品的实际品相或真实价格。
INSERT INTO product
    (id, sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order, version)
VALUES
    (1, 'K9-BGL-03', 101, '比格犬', '用于正常购买及并发补货演示，初始库存10。', '比格犬：宠物商城课程教学商品。用于正常购买及并发补货演示，初始库存10。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/K9-BGL-03/1.jpg', 1280.00, 10, 'ON_SALE', 10, 0),
    (2, 'K9-PUP-01', 101, '比格犬幼宠', '同分类的第二个教学商品。', '比格犬幼宠：宠物商城课程教学商品。同分类的第二个教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/K9-BGL-03/1.jpg', 1980.00, 4, 'ON_SALE', 20, 0),
    (3, 'K9-RTV-11', 102, '金毛犬养护套装', '沿用原型的养护套装素材。', '金毛犬养护套装：宠物商城课程教学商品。沿用原型的养护套装素材。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/K9-RTV-11/1.jpg', 168.00, 25, 'ON_SALE', 30, 0),
    (4, 'K9-LEASH-01', 102, '犬用出行套装', '用品类教学商品，展示两位小数金额。', '犬用出行套装：宠物商城课程教学商品。用品类教学商品，展示两位小数金额。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/K9-RTV-11/1.jpg', 49.90, 80, 'ON_SALE', 40, 0),
    (5, 'FL-DLH-02', 201, '成年波斯猫', '保留原型代表性SKU。', '成年波斯猫：宠物商城课程教学商品。保留原型代表性SKU。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/FL-DLH-02/1.jpg', 1800.00, 6, 'ON_SALE', 50, 0),
    (6, 'CT-SHORT-01', 201, '短毛猫', '低库存商品，购买2件可验证库存不足。', '短毛猫：宠物商城课程教学商品。低库存商品，购买2件可验证库存不足。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/FL-DLH-02/1.jpg', 950.00, 1, 'ON_SALE', 60, 0),
    (7, 'CT-SIA-05', 202, '暹罗猫养护套装', '可与其他商品组合验证整车下单。', '暹罗猫养护套装：宠物商城课程教学商品。可与其他商品组合验证整车下单。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/CT-SIA-05/1.jpg', 99.00, 20, 'ON_SALE', 70, 0),
    (8, 'CT-BED-01', 202, '猫咪舒适用品套装', '上架但零库存，用于售罄提示。', '猫咪舒适用品套装：宠物商城课程教学商品。上架但零库存，用于售罄提示。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/CT-SIA-05/1.jpg', 159.00, 0, 'ON_SALE', 80, 0),
    (9, 'AQ-KOI-04', 301, '观赏锦鲤', '水族宠物分类教学商品。', '观赏锦鲤：宠物商城课程教学商品。水族宠物分类教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/AQ-KOI-04/1.jpg', 68.00, 30, 'ON_SALE', 90, 0),
    (10, 'AQ-GOLD-01', 301, '观赏金鱼', '水族宠物第二个教学商品。', '观赏金鱼：宠物商城课程教学商品。水族宠物第二个教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/AQ-KOI-04/1.jpg', 19.90, 60, 'ON_SALE', 100, 0),
    (11, 'AQ-CRF-12', 302, '珊瑚礁水族套装', '沿用原型水族套装展示素材。', '珊瑚礁水族套装：宠物商城课程教学商品。沿用原型水族套装展示素材。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/AQ-CRF-12/1.jpg', 239.00, 15, 'ON_SALE', 110, 0),
    (12, 'AQ-TANK-01', 302, '水族入门用品套装', '下架商品，不得在用户端上架列表出现。', '水族入门用品套装：宠物商城课程教学商品。下架商品，不得在用户端上架列表出现。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/AQ-CRF-12/1.jpg', 399.00, 5, 'DRAFT', 120, 0),
    (13, 'BR-CNR-08', 401, '金丝雀', '鸟类宠物教学商品。', '金丝雀：宠物商城课程教学商品。鸟类宠物教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/BR-CNR-08/1.jpg', 128.00, 8, 'ON_SALE', 130, 0),
    (14, 'BR-FINCH-01', 401, '文鸟', '鸟类宠物第二个教学商品。', '文鸟：宠物商城课程教学商品。鸟类宠物第二个教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/BR-CNR-08/1.jpg', 88.00, 12, 'ON_SALE', 140, 0),
    (15, 'BRD-MAC-07', 402, '金刚鹦鹉养护套装', '鸟类用品教学商品。', '金刚鹦鹉养护套装：宠物商城课程教学商品。鸟类用品教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/BRD-MAC-07/1.jpg', 89.00, 40, 'ON_SALE', 150, 0),
    (16, 'BR-BOWL-01', 402, '鸟类日常用品套装', '用于数量修改与购物车合并演示。', '鸟类日常用品套装：宠物商城课程教学商品。用于数量修改与购物车合并演示。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/BRD-MAC-07/1.jpg', 25.00, 100, 'ON_SALE', 160, 0),
    (17, 'RP-SNK-02', 501, '玉米蛇', '爬宠低库存教学商品。', '玉米蛇：宠物商城课程教学商品。爬宠低库存教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/RP-SNK-02/1.jpg', 320.00, 3, 'ON_SALE', 170, 0),
    (18, 'RP-TURTLE-01', 501, '观赏龟', '爬宠分类第二个教学商品。', '观赏龟：宠物商城课程教学商品。爬宠分类第二个教学商品。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/RP-SNK-02/1.jpg', 168.00, 20, 'ON_SALE', 180, 0),
    (19, 'RP-IGU-09', 502, '绿鬣蜥养护套装', '沿用原型爬宠套装素材。', '绿鬣蜥养护套装：宠物商城课程教学商品。沿用原型爬宠套装素材。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/RP-IGU-09/1.jpg', 119.00, 15, 'ON_SALE', 190, 0),
    (20, 'RP-LAMP-01', 502, '爬宠环境用品套装', '第二个下架商品，用于后台筛选和上架练习。', '爬宠环境用品套装：宠物商城课程教学商品。第二个下架商品，用于后台筛选和上架练习。商品及价格仅用于教学演示；请通过实际下单观察订单快照与库存事务。', 'images/products/RP-IGU-09/1.jpg', 69.00, 12, 'DRAFT', 200, 0);

INSERT INTO product_image (id, product_id, image_path, alt_text, sort_order) VALUES
    (1, 1, 'images/products/K9-BGL-03/1.jpg', '比格犬·教学展示图1', 10),
    (2, 1, 'images/products/K9-BGL-03/2.jpg', '比格犬·教学展示图2', 20),
    (3, 1, 'images/products/K9-BGL-03/3.jpg', '比格犬·教学展示图3', 30),
    (4, 1, 'images/products/K9-BGL-03/4.jpg', '比格犬·教学展示图4', 40),
    (5, 2, 'images/products/K9-BGL-03/1.jpg', '比格犬幼宠·教学展示图1', 10),
    (6, 2, 'images/products/K9-BGL-03/2.jpg', '比格犬幼宠·教学展示图2', 20),
    (7, 2, 'images/products/K9-BGL-03/3.jpg', '比格犬幼宠·教学展示图3', 30),
    (8, 2, 'images/products/K9-BGL-03/4.jpg', '比格犬幼宠·教学展示图4', 40),
    (9, 3, 'images/products/K9-RTV-11/1.jpg', '金毛犬养护套装·教学展示图1', 10),
    (10, 3, 'images/products/K9-RTV-11/2.jpg', '金毛犬养护套装·教学展示图2', 20),
    (11, 3, 'images/products/K9-RTV-11/3.jpg', '金毛犬养护套装·教学展示图3', 30),
    (12, 3, 'images/products/K9-RTV-11/4.jpg', '金毛犬养护套装·教学展示图4', 40),
    (13, 4, 'images/products/K9-RTV-11/1.jpg', '犬用出行套装·教学展示图1', 10),
    (14, 4, 'images/products/K9-RTV-11/2.jpg', '犬用出行套装·教学展示图2', 20),
    (15, 4, 'images/products/K9-RTV-11/3.jpg', '犬用出行套装·教学展示图3', 30),
    (16, 4, 'images/products/K9-RTV-11/4.jpg', '犬用出行套装·教学展示图4', 40),
    (17, 5, 'images/products/FL-DLH-02/1.jpg', '成年波斯猫·教学展示图1', 10),
    (18, 5, 'images/products/FL-DLH-02/2.jpg', '成年波斯猫·教学展示图2', 20),
    (19, 5, 'images/products/FL-DLH-02/3.jpg', '成年波斯猫·教学展示图3', 30),
    (20, 5, 'images/products/FL-DLH-02/4.jpg', '成年波斯猫·教学展示图4', 40),
    (21, 6, 'images/products/FL-DLH-02/1.jpg', '短毛猫·教学展示图1', 10),
    (22, 6, 'images/products/FL-DLH-02/2.jpg', '短毛猫·教学展示图2', 20),
    (23, 6, 'images/products/FL-DLH-02/3.jpg', '短毛猫·教学展示图3', 30),
    (24, 6, 'images/products/FL-DLH-02/4.jpg', '短毛猫·教学展示图4', 40),
    (25, 7, 'images/products/CT-SIA-05/1.jpg', '暹罗猫养护套装·教学展示图1', 10),
    (26, 7, 'images/products/CT-SIA-05/2.jpg', '暹罗猫养护套装·教学展示图2', 20),
    (27, 7, 'images/products/CT-SIA-05/3.jpg', '暹罗猫养护套装·教学展示图3', 30),
    (28, 7, 'images/products/CT-SIA-05/4.jpg', '暹罗猫养护套装·教学展示图4', 40),
    (29, 8, 'images/products/CT-SIA-05/1.jpg', '猫咪舒适用品套装·教学展示图1', 10),
    (30, 8, 'images/products/CT-SIA-05/2.jpg', '猫咪舒适用品套装·教学展示图2', 20),
    (31, 8, 'images/products/CT-SIA-05/3.jpg', '猫咪舒适用品套装·教学展示图3', 30),
    (32, 8, 'images/products/CT-SIA-05/4.jpg', '猫咪舒适用品套装·教学展示图4', 40),
    (33, 9, 'images/products/AQ-KOI-04/1.jpg', '观赏锦鲤·教学展示图1', 10),
    (34, 9, 'images/products/AQ-KOI-04/2.jpg', '观赏锦鲤·教学展示图2', 20),
    (35, 9, 'images/products/AQ-KOI-04/3.jpg', '观赏锦鲤·教学展示图3', 30),
    (36, 9, 'images/products/AQ-KOI-04/4.jpg', '观赏锦鲤·教学展示图4', 40),
    (37, 10, 'images/products/AQ-KOI-04/1.jpg', '观赏金鱼·教学展示图1', 10),
    (38, 10, 'images/products/AQ-KOI-04/2.jpg', '观赏金鱼·教学展示图2', 20),
    (39, 10, 'images/products/AQ-KOI-04/3.jpg', '观赏金鱼·教学展示图3', 30),
    (40, 10, 'images/products/AQ-KOI-04/4.jpg', '观赏金鱼·教学展示图4', 40),
    (41, 11, 'images/products/AQ-CRF-12/1.jpg', '珊瑚礁水族套装·教学展示图1', 10),
    (42, 11, 'images/products/AQ-CRF-12/2.jpg', '珊瑚礁水族套装·教学展示图2', 20),
    (43, 11, 'images/products/AQ-CRF-12/3.jpg', '珊瑚礁水族套装·教学展示图3', 30),
    (44, 11, 'images/products/AQ-CRF-12/4.jpg', '珊瑚礁水族套装·教学展示图4', 40),
    (45, 12, 'images/products/AQ-CRF-12/1.jpg', '水族入门用品套装·教学展示图1', 10),
    (46, 12, 'images/products/AQ-CRF-12/2.jpg', '水族入门用品套装·教学展示图2', 20),
    (47, 12, 'images/products/AQ-CRF-12/3.jpg', '水族入门用品套装·教学展示图3', 30),
    (48, 12, 'images/products/AQ-CRF-12/4.jpg', '水族入门用品套装·教学展示图4', 40),
    (49, 13, 'images/products/BR-CNR-08/1.jpg', '金丝雀·教学展示图1', 10),
    (50, 13, 'images/products/BR-CNR-08/2.jpg', '金丝雀·教学展示图2', 20),
    (51, 13, 'images/products/BR-CNR-08/3.jpg', '金丝雀·教学展示图3', 30),
    (52, 13, 'images/products/BR-CNR-08/4.jpg', '金丝雀·教学展示图4', 40),
    (53, 14, 'images/products/BR-CNR-08/1.jpg', '文鸟·教学展示图1', 10),
    (54, 14, 'images/products/BR-CNR-08/2.jpg', '文鸟·教学展示图2', 20),
    (55, 14, 'images/products/BR-CNR-08/3.jpg', '文鸟·教学展示图3', 30),
    (56, 14, 'images/products/BR-CNR-08/4.jpg', '文鸟·教学展示图4', 40),
    (57, 15, 'images/products/BRD-MAC-07/1.jpg', '金刚鹦鹉养护套装·教学展示图1', 10),
    (58, 15, 'images/products/BRD-MAC-07/2.jpg', '金刚鹦鹉养护套装·教学展示图2', 20),
    (59, 15, 'images/products/BRD-MAC-07/3.jpg', '金刚鹦鹉养护套装·教学展示图3', 30),
    (60, 15, 'images/products/BRD-MAC-07/4.jpg', '金刚鹦鹉养护套装·教学展示图4', 40),
    (61, 16, 'images/products/BRD-MAC-07/1.jpg', '鸟类日常用品套装·教学展示图1', 10),
    (62, 16, 'images/products/BRD-MAC-07/2.jpg', '鸟类日常用品套装·教学展示图2', 20),
    (63, 16, 'images/products/BRD-MAC-07/3.jpg', '鸟类日常用品套装·教学展示图3', 30),
    (64, 16, 'images/products/BRD-MAC-07/4.jpg', '鸟类日常用品套装·教学展示图4', 40),
    (65, 17, 'images/products/RP-SNK-02/1.jpg', '玉米蛇·教学展示图1', 10),
    (66, 17, 'images/products/RP-SNK-02/2.jpg', '玉米蛇·教学展示图2', 20),
    (67, 17, 'images/products/RP-SNK-02/3.jpg', '玉米蛇·教学展示图3', 30),
    (68, 17, 'images/products/RP-SNK-02/4.jpg', '玉米蛇·教学展示图4', 40),
    (69, 18, 'images/products/RP-SNK-02/1.jpg', '观赏龟·教学展示图1', 10),
    (70, 18, 'images/products/RP-SNK-02/2.jpg', '观赏龟·教学展示图2', 20),
    (71, 18, 'images/products/RP-SNK-02/3.jpg', '观赏龟·教学展示图3', 30),
    (72, 18, 'images/products/RP-SNK-02/4.jpg', '观赏龟·教学展示图4', 40),
    (73, 19, 'images/products/RP-IGU-09/1.jpg', '绿鬣蜥养护套装·教学展示图1', 10),
    (74, 19, 'images/products/RP-IGU-09/2.jpg', '绿鬣蜥养护套装·教学展示图2', 20),
    (75, 19, 'images/products/RP-IGU-09/3.jpg', '绿鬣蜥养护套装·教学展示图3', 30),
    (76, 19, 'images/products/RP-IGU-09/4.jpg', '绿鬣蜥养护套装·教学展示图4', 40),
    (77, 20, 'images/products/RP-IGU-09/1.jpg', '爬宠环境用品套装·教学展示图1', 10),
    (78, 20, 'images/products/RP-IGU-09/2.jpg', '爬宠环境用品套装·教学展示图2', 20),
    (79, 20, 'images/products/RP-IGU-09/3.jpg', '爬宠环境用品套装·教学展示图3', 30),
    (80, 20, 'images/products/RP-IGU-09/4.jpg', '爬宠环境用品套装·教学展示图4', 40);

-- ===== 用品演示数据开始 =====
-- 新增20个用品SKU：每个用品分类4个，18个上架（含1个售罄）、2个下架。
-- 不指定主键，避免与已有数据冲突；仅补充不存在的SKU，不修改旧商品或库存。
-- 缺失约定分类时category_id会违反非空约束，本条批量INSERT整体失败。
-- 封面使用static/images/supplies中的SVG教学示意图，不冒充商品实物照片。
INSERT INTO product
    (sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order, version)
SELECT seed.sku,
       (SELECT c.id FROM category c WHERE c.code = seed.category_code),
       seed.name, seed.summary, seed.description, seed.cover_image,
       seed.price, seed.stock, seed.status, seed.sort_order, 0
FROM (
    SELECT 'DOG-LEASH-26' AS sku, 'DOGS_SUPPLIES' AS category_code, '轻便反光牵引绳' AS name, '日常散步用反光牵引绳，单条装。' AS summary, '轻便反光牵引绳。日常散步用反光牵引绳，单条装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。' AS description, 'images/supplies/DOG-LEASH-26.svg' AS cover_image, 39.90 AS price, 42 AS stock, 'ON_SALE' AS status, 1 AS sort_order
    UNION ALL
    SELECT 'CAT-LITTER-26', 'CATS_SUPPLIES', '豆腐猫砂2.5kg', '原味豆腐猫砂，2.5kg单袋装。', '豆腐猫砂2.5kg。原味豆腐猫砂，2.5kg单袋装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/CAT-LITTER-26.svg', 32.80, 75, 'ON_SALE', 2
    UNION ALL
    SELECT 'FISH-FILTER-26', 'FISH_SUPPLIES', '鱼缸静音过滤器', '小型鱼缸用过滤器，单台装。', '鱼缸静音过滤器。小型鱼缸用过滤器，单台装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/FISH-FILTER-26.svg', 89.00, 18, 'ON_SALE', 3
    UNION ALL
    SELECT 'BIRD-PERCH-26', 'BIRDS_SUPPLIES', '天然木质站杆', '鸟笼配套木质站杆，双支装。', '天然木质站杆。鸟笼配套木质站杆，双支装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/BIRD-PERCH-26.svg', 24.90, 55, 'ON_SALE', 4
    UNION ALL
    SELECT 'REP-HIDE-26', 'REPTILES_SUPPLIES', '爬宠树洞躲避屋', '爬宠环境布置用树洞造型躲避屋。', '爬宠树洞躲避屋。爬宠环境布置用树洞造型躲避屋。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/REP-HIDE-26.svg', 46.00, 23, 'ON_SALE', 5
    UNION ALL
    SELECT 'DOG-BRUSH-26', 'DOGS_SUPPLIES', '犬用双面梳', '双面梳理工具，适合日常毛发整理。', '犬用双面梳。双面梳理工具，适合日常毛发整理。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/DOG-BRUSH-26.svg', 26.50, 60, 'ON_SALE', 21
    UNION ALL
    SELECT 'CAT-BED-26', 'CATS_SUPPLIES', '可拆洗猫窝', '灰色单规格猫窝，可拆洗外套。', '可拆洗猫窝。灰色单规格猫窝，可拆洗外套。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/CAT-BED-26.svg', 129.00, 16, 'ON_SALE', 22
    UNION ALL
    SELECT 'FISH-FOOD-26', 'FISH_SUPPLIES', '观赏鱼颗粒饲料100g', '观赏鱼日常投喂颗粒，100g单罐。', '观赏鱼颗粒饲料100g。观赏鱼日常投喂颗粒，100g单罐。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/FISH-FOOD-26.svg', 18.90, 120, 'ON_SALE', 23
    UNION ALL
    SELECT 'BIRD-FOOD-26', 'BIRDS_SUPPLIES', '鸟用混合谷物500g', '鸟类用品教学商品，500g单袋装。', '鸟用混合谷物500g。鸟类用品教学商品，500g单袋装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/BIRD-FOOD-26.svg', 29.80, 80, 'ON_SALE', 24
    UNION ALL
    SELECT 'REP-HEAT-26', 'REPTILES_SUPPLIES', '爬宠加热垫', '下架教学样例，供后台上架练习。', '爬宠加热垫。下架教学样例，供后台上架练习。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/REP-HEAT-26.svg', 68.00, 12, 'DRAFT', 25
    UNION ALL
    SELECT 'DOG-BOWL-26', 'DOGS_SUPPLIES', '不锈钢防滑食盆', '单规格食盆，底部配防滑圈。', '不锈钢防滑食盆。单规格食盆，底部配防滑圈。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/DOG-BOWL-26.svg', 35.00, 48, 'ON_SALE', 31
    UNION ALL
    SELECT 'CAT-TEASER-26', 'CATS_SUPPLIES', '羽毛逗猫棒', '日常互动玩具，单支装。', '羽毛逗猫棒。日常互动玩具，单支装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/CAT-TEASER-26.svg', 15.90, 96, 'ON_SALE', 32
    UNION ALL
    SELECT 'FISH-GRAVEL-26', 'FISH_SUPPLIES', '鱼缸造景底砂1kg', '水族造景用浅色底砂，1kg单袋。', '鱼缸造景底砂1kg。水族造景用浅色底砂，1kg单袋。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/FISH-GRAVEL-26.svg', 22.00, 64, 'ON_SALE', 33
    UNION ALL
    SELECT 'BIRD-BATH-26', 'BIRDS_SUPPLIES', '鸟用挂式浴盆', '上架零库存样例，可展示但不能购买。', '鸟用挂式浴盆。上架零库存样例，可展示但不能购买。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/BIRD-BATH-26.svg', 38.00, 0, 'ON_SALE', 34
    UNION ALL
    SELECT 'REP-THERMO-26', 'REPTILES_SUPPLIES', '爬宠数字温湿度计', '饲养环境温湿度显示工具，单个装。', '爬宠数字温湿度计。饲养环境温湿度显示工具，单个装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/REP-THERMO-26.svg', 42.90, 36, 'ON_SALE', 35
    UNION ALL
    SELECT 'REP-DISH-26', 'REPTILES_SUPPLIES', '爬宠浅口食盆', '岩石造型浅口食盆，单个装。', '爬宠浅口食盆。岩石造型浅口食盆，单个装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/REP-DISH-26.svg', 19.90, 70, 'ON_SALE', 41
    UNION ALL
    SELECT 'BIRD-COVER-26', 'BIRDS_SUPPLIES', '透气鸟笼罩', '单规格浅色鸟笼罩，便于日常整理。', '透气鸟笼罩。单规格浅色鸟笼罩，便于日常整理。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/BIRD-COVER-26.svg', 45.00, 28, 'ON_SALE', 42
    UNION ALL
    SELECT 'CAT-SCRATCH-26', 'CATS_SUPPLIES', '瓦楞纸猫抓板', '可替换式瓦楞纸抓板，单块装。', '瓦楞纸猫抓板。可替换式瓦楞纸抓板，单块装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/CAT-SCRATCH-26.svg', 27.90, 88, 'ON_SALE', 43
    UNION ALL
    SELECT 'DOG-FOOD-26', 'DOGS_SUPPLIES', '成年犬日常粮2kg', '成年犬用品教学样例，2kg单袋装。', '成年犬日常粮2kg。成年犬用品教学样例，2kg单袋装。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/DOG-FOOD-26.svg', 98.00, 56, 'ON_SALE', 44
    UNION ALL
    SELECT 'FISH-TEST-26', 'FISH_SUPPLIES', '水族水质测试盒', '下架教学样例，供筛选与状态验证。', '水族水质测试盒。下架教学样例，供筛选与状态验证。 所有名称、价格和库存仅用于课程演示，不构成真实销售或使用建议。一个商品对应一个固定SKU，不提供规格或服务选项。', 'images/supplies/FISH-TEST-26.svg', 59.90, 10, 'DRAFT', 45
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product existing WHERE existing.sku = seed.sku);
-- 新用品不预置相册，详情页按规约回退封面；保留无相册验证场景。
-- ===== 用品演示数据结束 =====

-- ===== 宠物演示数据开始 =====
-- 新增20个宠物SKU：五个宠物二级分类各4个；18个上架（含1个售罄）、2个下架。
-- 按SKU补缺，不覆盖原商品、库存、排序、时间或版本；分类缺失则批量插入失败。
-- 新宠物金毛、英短的sort_order=0，与用品排序1、2组成首页精选。
-- 使用真实录入时间，不伪造未来时间；完整初始化时最后录入的布偶猫、孔雀鱼、柯基进入新品区。
INSERT INTO product
    (sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order, version)
SELECT seed.sku,
       (SELECT c.id FROM category c WHERE c.code = seed.category_code),
       seed.name, seed.summary, seed.description, seed.cover_image,
       seed.price, seed.stock, seed.status, seed.sort_order, 0
FROM (
    SELECT 1 AS seed_order, 'DOG-GOLDEN-26' AS sku, 'DOGS_PETS' AS category_code, '金毛寻回犬' AS name, '金毛寻回犬品种展示，单SKU教学商品。' AS summary, '金毛寻回犬。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。' AS description, 'images/pets/DOG-GOLDEN-26.svg' AS cover_image, 2200.00 AS price, 8 AS stock, 'ON_SALE' AS status, 0 AS sort_order
    UNION ALL
    SELECT 2, 'CAT-BRITISH-26', 'CATS_PETS', '英国短毛猫', '英国短毛猫品种展示，单SKU教学商品。', '英国短毛猫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/CAT-BRITISH-26.svg', 1800.00, 6, 'ON_SALE', 0
    UNION ALL
    SELECT 3, 'DOG-LABRADOR-26', 'DOGS_PETS', '拉布拉多寻回犬', '拉布拉多寻回犬品种展示，单SKU教学商品。', '拉布拉多寻回犬。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/DOG-LABRADOR-26.svg', 1800.00, 7, 'ON_SALE', 51
    UNION ALL
    SELECT 4, 'DOG-SHIBA-26', 'DOGS_PETS', '柴犬', '柴犬品种展示，单SKU教学商品。', '柴犬。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/DOG-SHIBA-26.svg', 2600.00, 5, 'ON_SALE', 52
    UNION ALL
    SELECT 5, 'CAT-AMERICAN-26', 'CATS_PETS', '美国短毛猫', '美国短毛猫品种展示，单SKU教学商品。', '美国短毛猫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/CAT-AMERICAN-26.svg', 1500.00, 9, 'ON_SALE', 53
    UNION ALL
    SELECT 6, 'CAT-SIAMESE-26', 'CATS_PETS', '暹罗猫', '暹罗猫品种展示，单SKU教学商品。', '暹罗猫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/CAT-SIAMESE-26.svg', 1600.00, 4, 'ON_SALE', 54
    UNION ALL
    SELECT 7, 'FISH-BETTA-26', 'FISH_PETS', '暹罗斗鱼', '暹罗斗鱼品种展示，单SKU教学商品。', '暹罗斗鱼。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/FISH-BETTA-26.svg', 68.00, 24, 'ON_SALE', 55
    UNION ALL
    SELECT 8, 'FISH-NEON-26', 'FISH_PETS', '霓虹灯鱼', '霓虹灯鱼品种展示，单SKU教学商品。', '霓虹灯鱼。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/FISH-NEON-26.svg', 8.00, 100, 'ON_SALE', 56
    UNION ALL
    SELECT 9, 'FISH-GOLDFISH-26', 'FISH_PETS', '金鱼', '金鱼品种展示，单SKU教学商品。', '金鱼。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/FISH-GOLDFISH-26.svg', 25.00, 45, 'ON_SALE', 57
    UNION ALL
    SELECT 10, 'BIRD-BUDGIE-26', 'BIRDS_PETS', '虎皮鹦鹉', '虎皮鹦鹉品种展示，单SKU教学商品。', '虎皮鹦鹉。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/BIRD-BUDGIE-26.svg', 128.00, 15, 'ON_SALE', 58
    UNION ALL
    SELECT 11, 'BIRD-COCKATIEL-26', 'BIRDS_PETS', '玄凤鹦鹉', '玄凤鹦鹉品种展示，单SKU教学商品。', '玄凤鹦鹉。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/BIRD-COCKATIEL-26.svg', 450.00, 8, 'ON_SALE', 59
    UNION ALL
    SELECT 12, 'BIRD-LOVEBIRD-26', 'BIRDS_PETS', '桃脸牡丹鹦鹉', '桃脸牡丹鹦鹉品种展示，单SKU教学商品。', '桃脸牡丹鹦鹉。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/BIRD-LOVEBIRD-26.svg', 288.00, 10, 'ON_SALE', 60
    UNION ALL
    SELECT 13, 'BIRD-FINCH-26', 'BIRDS_PETS', '斑胸草雀', '上架售罄样例，用于验证零库存商品的展示。', '斑胸草雀。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/BIRD-FINCH-26.svg', 80.00, 0, 'ON_SALE', 61
    UNION ALL
    SELECT 14, 'REP-LEOPARD-26', 'REPTILES_PETS', '豹纹守宫', '豹纹守宫品种展示，单SKU教学商品。', '豹纹守宫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/REP-LEOPARD-26.svg', 380.00, 12, 'ON_SALE', 62
    UNION ALL
    SELECT 15, 'REP-CRESTED-26', 'REPTILES_PETS', '睫角守宫', '睫角守宫品种展示，单SKU教学商品。', '睫角守宫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/REP-CRESTED-26.svg', 680.00, 6, 'ON_SALE', 63
    UNION ALL
    SELECT 16, 'REP-CORN-26', 'REPTILES_PETS', '玉米蛇', '下架教学样例，用于后台筛选和上架练习。', '玉米蛇。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/REP-CORN-26.svg', 480.00, 5, 'DRAFT', 64
    UNION ALL
    SELECT 17, 'REP-BEARDED-26', 'REPTILES_PETS', '鬃狮蜥', '下架教学样例，用于后台筛选和上架练习。', '鬃狮蜥。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/REP-BEARDED-26.svg', 880.00, 3, 'DRAFT', 65
    UNION ALL
    SELECT 18, 'DOG-CORGI-26', 'DOGS_PETS', '威尔士柯基犬', '威尔士柯基犬品种展示，单SKU教学商品。', '威尔士柯基犬。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/DOG-CORGI-26.svg', 2800.00, 6, 'ON_SALE', 66
    UNION ALL
    SELECT 19, 'FISH-GUPPY-26', 'FISH_PETS', '孔雀鱼', '孔雀鱼品种展示，单SKU教学商品。', '孔雀鱼。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/FISH-GUPPY-26.svg', 12.00, 80, 'ON_SALE', 67
    UNION ALL
    SELECT 20, 'CAT-RAGDOLL-26', 'CATS_PETS', '布偶猫', '布偶猫品种展示，单SKU教学商品。', '布偶猫。图片用于品种示意，不对应特定待售个体；价格与库存为课程演示数据。一个商品对应一个固定SKU，不引入年龄、性别或毛色规格。', 'images/pets/CAT-RAGDOLL-26.svg', 3800.00, 4, 'ON_SALE', 68
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product existing WHERE existing.sku = seed.sku)
ORDER BY seed.seed_order;

-- 每个新增宠物提供一张相册图；不写死product_id，兼容已有库的自增编号。
-- 同一商品已有相同路径时跳过；重复执行不会重复添加图片。
INSERT INTO product_image (product_id, image_path, alt_text, sort_order)
SELECT p.id, p.cover_image, CONCAT(p.name, '·品种示意图'), 10
FROM product p
WHERE p.sku IN ('DOG-GOLDEN-26', 'CAT-BRITISH-26', 'DOG-LABRADOR-26', 'DOG-SHIBA-26', 'CAT-AMERICAN-26', 'CAT-SIAMESE-26', 'FISH-BETTA-26', 'FISH-NEON-26', 'FISH-GOLDFISH-26', 'BIRD-BUDGIE-26', 'BIRD-COCKATIEL-26', 'BIRD-LOVEBIRD-26', 'BIRD-FINCH-26', 'REP-LEOPARD-26', 'REP-CRESTED-26', 'REP-CORN-26', 'REP-BEARDED-26', 'DOG-CORGI-26', 'FISH-GUPPY-26', 'CAT-RAGDOLL-26')
  AND NOT EXISTS (
      SELECT 1 FROM product_image pi WHERE pi.product_id = p.id AND pi.image_path = p.cover_image
  );
-- ===== 宠物演示数据结束 =====

-- ===== 交易演示数据开始 =====
-- alice、bob各五张订单、每单两条明细；覆盖五种状态，六张已支付订单使用MOCK凭证。
-- 现有模型没有独立支付表，失败支付不留成功凭证，订单仍为PENDING_PAYMENT。
-- 订单号DEMO-202611-*专用于本组教学样例；按订单号补缺，保留已存在订单的真实后续状态。
-- 购物车是两个账号的当前选购，与历史订单独立；订单中的商品不再次从当前车删除。
-- 临时表只用于本次导入，不新增持久业务表。请完整执行，发生错误立即停止并回滚。
DROP TEMPORARY TABLE IF EXISTS _petstore_demo_guard, _petstore_demo_orders,
    _petstore_demo_items, _petstore_demo_cart, _petstore_demo_stock;
CREATE TEMPORARY TABLE _petstore_demo_guard (
    check_name VARCHAR(100) NOT NULL,
    ok TINYINT NOT NULL,
    CONSTRAINT ck_petstore_demo_guard CHECK (ok = 1)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _petstore_demo_orders (
    order_no VARCHAR(40) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL,
    age_hours INT NOT NULL,
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    receiver_address VARCHAR(255) NOT NULL,
    user_id BIGINT NULL,
    is_new TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    shipped_at DATETIME NULL,
    completed_at DATETIME NULL
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _petstore_demo_items (
    order_no VARCHAR(40) NOT NULL,
    sku VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    product_id BIGINT NULL,
    PRIMARY KEY (order_no, sku)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _petstore_demo_cart (
    username VARCHAR(50) NOT NULL,
    sku VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    user_id BIGINT NULL,
    product_id BIGINT NULL,
    PRIMARY KEY (username, sku)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE _petstore_demo_stock (
    product_id BIGINT PRIMARY KEY,
    quantity INT NOT NULL
) ENGINE=InnoDB;

-- 与后续业务约定一致：先锁两个用户，再锁商品。
-- 仅用于空库初始化，不在应用正常处理交易时并行执行初始化脚本。
SELECT id AS locked_user_id FROM store_user WHERE username IN ('alice', 'bob') ORDER BY id FOR UPDATE;
SELECT id AS locked_product_id FROM product ORDER BY id FOR UPDATE;
SET @petstore_demo_seed_now = CURRENT_TIMESTAMP;

INSERT INTO _petstore_demo_orders
    (order_no, username, status, age_hours, receiver_name, receiver_phone, receiver_address)
VALUES
    ('DEMO-202611-AL-01', 'alice', 'PENDING_PAYMENT', 1, '林同学', '13800000001', '教学示例地址：长沙市岳麓区课程实验室A（非真实收货地址）'),
    ('DEMO-202611-AL-02', 'alice', 'PAID', 25, '林同学', '13800000001', '教学示例地址：长沙市岳麓区课程实验室A（非真实收货地址）'),
    ('DEMO-202611-AL-03', 'alice', 'SHIPPED', 72, '林同学', '13800000001', '教学示例地址：长沙市岳麓区课程实验室A（非真实收货地址）'),
    ('DEMO-202611-AL-04', 'alice', 'COMPLETED', 168, '林同学', '13800000001', '教学示例地址：长沙市岳麓区课程实验室A（非真实收货地址）'),
    ('DEMO-202611-AL-05', 'alice', 'CANCELLED', 48, '林同学', '13800000001', '教学示例地址：长沙市岳麓区课程实验室A（非真实收货地址）'),
    ('DEMO-202611-BO-01', 'bob', 'PENDING_PAYMENT', 2, '陈同学', '13800000002', '教学示例地址：长沙市岳麓区课程实验室B（非真实收货地址）'),
    ('DEMO-202611-BO-02', 'bob', 'PAID', 49, '陈同学', '13800000002', '教学示例地址：长沙市岳麓区课程实验室B（非真实收货地址）'),
    ('DEMO-202611-BO-03', 'bob', 'SHIPPED', 96, '陈同学', '13800000002', '教学示例地址：长沙市岳麓区课程实验室B（非真实收货地址）'),
    ('DEMO-202611-BO-04', 'bob', 'COMPLETED', 192, '陈同学', '13800000002', '教学示例地址：长沙市岳麓区课程实验室B（非真实收货地址）'),
    ('DEMO-202611-BO-05', 'bob', 'CANCELLED', 24, '陈同学', '13800000002', '教学示例地址：长沙市岳麓区课程实验室B（非真实收货地址）');
INSERT INTO _petstore_demo_items (order_no, sku, quantity, unit_price) VALUES
    ('DEMO-202611-AL-01', 'CAT-LITTER-26', 2, 32.80),
    ('DEMO-202611-AL-01', 'DOG-LEASH-26', 1, 39.90),
    ('DEMO-202611-AL-02', 'CAT-RAGDOLL-26', 1, 3800.00),
    ('DEMO-202611-AL-02', 'CAT-BED-26', 1, 129.00),
    ('DEMO-202611-AL-03', 'DOG-FOOD-26', 2, 88.00),
    ('DEMO-202611-AL-03', 'DOG-BOWL-26', 1, 35.00),
    ('DEMO-202611-AL-04', 'FISH-GUPPY-26', 5, 12.00),
    ('DEMO-202611-AL-04', 'FISH-FILTER-26', 1, 89.00),
    ('DEMO-202611-AL-05', 'DOG-CORGI-26', 1, 2800.00),
    ('DEMO-202611-AL-05', 'DOG-LEASH-26', 2, 39.90),
    ('DEMO-202611-BO-01', 'FISH-NEON-26', 10, 8.00),
    ('DEMO-202611-BO-01', 'FISH-FOOD-26', 1, 18.90),
    ('DEMO-202611-BO-02', 'BIRD-BUDGIE-26', 1, 128.00),
    ('DEMO-202611-BO-02', 'BIRD-PERCH-26', 2, 24.90),
    ('DEMO-202611-BO-03', 'REP-LEOPARD-26', 1, 380.00),
    ('DEMO-202611-BO-03', 'REP-HIDE-26', 1, 46.00),
    ('DEMO-202611-BO-04', 'CAT-AMERICAN-26', 1, 1380.00),
    ('DEMO-202611-BO-04', 'CAT-TEASER-26', 2, 15.90),
    ('DEMO-202611-BO-05', 'DOG-GOLDEN-26', 1, 2200.00),
    ('DEMO-202611-BO-05', 'DOG-BRUSH-26', 1, 26.50);
INSERT INTO _petstore_demo_cart (username, sku, quantity) VALUES
    ('alice', 'DOG-LEASH-26', 2),
    ('alice', 'CAT-LITTER-26', 3),
    ('alice', 'CAT-RAGDOLL-26', 1),
    ('alice', 'CT-SHORT-01', 2),
    ('alice', 'BIRD-BATH-26', 1),
    ('alice', 'REP-HEAT-26', 1),
    ('alice', 'FISH-TEST-26', 1),
    ('bob', 'DOG-LEASH-26', 1),
    ('bob', 'FISH-GUPPY-26', 5),
    ('bob', 'CAT-BED-26', 1);
-- 说明：alice 的购物车故意混入下架 / 售罄 / 有库存的商品，用来演示“不可结算商品”的拦截；
-- bob 的购物车全部可结算，用来演示从购物车 → 确认订单 → 模拟支付的完整主流程。

-- 所有外键通过用户名及SKU查找，不写死数据库自增ID。
UPDATE _petstore_demo_orders d LEFT JOIN store_user u ON u.username=d.username
SET d.user_id=u.id;
UPDATE _petstore_demo_items d LEFT JOIN product p ON p.sku=d.sku
SET d.product_id=p.id;
UPDATE _petstore_demo_cart d LEFT JOIN store_user u ON u.username=d.username
LEFT JOIN product p ON p.sku=d.sku SET d.user_id=u.id, d.product_id=p.id;
INSERT INTO _petstore_demo_guard
SELECT 'required customers and products', IF(
    NOT EXISTS (SELECT 1 FROM _petstore_demo_orders d LEFT JOIN store_user u ON u.id=d.user_id
                WHERE u.id IS NULL OR u.role<>'CUSTOMER' OR u.status<>'ENABLED')
    AND NOT EXISTS (SELECT 1 FROM _petstore_demo_items WHERE product_id IS NULL)
    AND NOT EXISTS (SELECT 1 FROM _petstore_demo_cart WHERE user_id IS NULL OR product_id IS NULL), 1, 0);
INSERT INTO _petstore_demo_guard
SELECT 'reserved order numbers belong to expected users', IF(
    NOT EXISTS (SELECT 1 FROM _petstore_demo_orders d JOIN sales_order o ON o.order_no=d.order_no
                WHERE o.user_id<>d.user_id), 1, 0);
UPDATE _petstore_demo_orders d LEFT JOIN sales_order o ON o.order_no=d.order_no
SET d.is_new=IF(o.id IS NULL, 1, 0);

-- 只对本次新建且未取消的订单净扣库存。取消样例表示历史上已恢复，净影响为0。
INSERT INTO _petstore_demo_stock (product_id, quantity)
SELECT i.product_id, SUM(i.quantity) FROM _petstore_demo_items i
JOIN _petstore_demo_orders d ON d.order_no=i.order_no
WHERE d.is_new=1 AND d.status<>'CANCELLED' GROUP BY i.product_id;
INSERT INTO _petstore_demo_guard
SELECT 'sufficient stock for new active orders', IF(
    NOT EXISTS (SELECT 1 FROM _petstore_demo_stock s JOIN product p ON p.id=s.product_id
                WHERE p.stock<s.quantity), 1, 0);
INSERT INTO _petstore_demo_guard
SELECT 'cart sku count stays within 20', IF(
    NOT EXISTS (
        SELECT d.user_id FROM _petstore_demo_cart d
        LEFT JOIN cart_item c ON c.user_id=d.user_id AND c.product_id=d.product_id
        WHERE c.id IS NULL GROUP BY d.user_id
        HAVING COUNT(*) + (SELECT COUNT(*) FROM cart_item old WHERE old.user_id=d.user_id) > 20
    ), 1, 0);

-- 时间以本次导入为基准生成过去1小时至8天的教学订单，状态时间按先后关系设置。
UPDATE _petstore_demo_orders SET created_at=TIMESTAMPADD(HOUR, -age_hours, @petstore_demo_seed_now);
UPDATE _petstore_demo_orders SET
    paid_at=CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN TIMESTAMPADD(MINUTE,5,created_at) END,
    cancelled_at=CASE WHEN status='CANCELLED' THEN TIMESTAMPADD(MINUTE,15,created_at) END,
    shipped_at=CASE WHEN status IN ('SHIPPED','COMPLETED') THEN TIMESTAMPADD(HOUR,24,created_at) END,
    completed_at=CASE WHEN status='COMPLETED' THEN TIMESTAMPADD(HOUR,72,created_at) END;

INSERT INTO sales_order
    (order_no, user_id, status, total_amount, receiver_name, receiver_phone, receiver_address,
     created_at, updated_at, payment_method, payment_no, paid_at, cancelled_at, shipped_at, completed_at)
SELECT d.order_no, d.user_id, d.status, totals.amount, d.receiver_name, d.receiver_phone, d.receiver_address,
       d.created_at, COALESCE(d.completed_at,d.shipped_at,d.cancelled_at,d.paid_at,d.created_at),
       CASE WHEN d.paid_at IS NOT NULL THEN 'MOCK' END,
       CASE WHEN d.paid_at IS NOT NULL THEN CONCAT('MOCK-',d.order_no) END,
       d.paid_at, d.cancelled_at, d.shipped_at, d.completed_at
FROM _petstore_demo_orders d
JOIN (SELECT order_no, SUM(unit_price*quantity) AS amount FROM _petstore_demo_items GROUP BY order_no) totals
ON totals.order_no=d.order_no
WHERE d.is_new=1 ORDER BY d.order_no;

-- 成交单价直接存快照，不在后续查询时回填当前价。两张完成/发货订单专门展示历史价差异。
INSERT INTO sales_order_item
    (order_id, product_id, sku_snapshot, product_name_snapshot, cover_image_snapshot, unit_price, quantity, line_amount)
SELECT o.id, p.id, p.sku, p.name, p.cover_image, i.unit_price, i.quantity, i.unit_price*i.quantity
FROM _petstore_demo_items i JOIN _petstore_demo_orders d ON d.order_no=i.order_no
JOIN sales_order o ON o.order_no=d.order_no JOIN product p ON p.id=i.product_id
WHERE d.is_new=1 ORDER BY o.id,p.id;
UPDATE product p JOIN _petstore_demo_stock s ON s.product_id=p.id
SET p.stock=p.stock-s.quantity, p.version=p.version+1;

-- 加车不扣库存，按用户+商品补缺；不覆盖已有条目的数量、版本或时间。
-- 失效条目模拟用户加车后商品下架、售罄或库存下降，供购物车展示与校验使用。
INSERT INTO cart_item (user_id, product_id, quantity, version)
SELECT d.user_id, d.product_id, d.quantity, 0 FROM _petstore_demo_cart d
WHERE NOT EXISTS (SELECT 1 FROM cart_item c WHERE c.user_id=d.user_id AND c.product_id=d.product_id)
ORDER BY d.user_id,d.product_id;

DROP TEMPORARY TABLE _petstore_demo_guard, _petstore_demo_orders,
    _petstore_demo_items, _petstore_demo_cart, _petstore_demo_stock;
-- ===== 交易演示数据结束 =====
COMMIT;
-- ===== 演示数据结束 =====

-- 导入后核对：预期4、15、60、100、10、10、20（54个上架，6个下架，3个上架售罄）。
SELECT 'store_user' AS table_name, COUNT(*) AS row_count FROM store_user
UNION ALL SELECT 'category', COUNT(*) FROM category
UNION ALL SELECT 'product', COUNT(*) FROM product
UNION ALL SELECT 'product_image', COUNT(*) FROM product_image
UNION ALL SELECT 'cart_item', COUNT(*) FROM cart_item
UNION ALL SELECT 'sales_order', COUNT(*) FROM sales_order
UNION ALL SELECT 'sales_order_item', COUNT(*) FROM sales_order_item;
