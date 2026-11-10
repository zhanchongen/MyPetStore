-- MyPetStore实验一：仅表结构（不含业务数据）
-- 适用：MySQL 8.0.16+ / 8.4，InnoDB，utf8mb4。
-- 与02-schema-with-demo.sql二选一，不按编号依次执行。
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
