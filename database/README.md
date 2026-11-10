# 数据库初始化说明

本目录是实验一 **唯一的共享 SQL 来源**。用户端与管理端连接同一个 MySQL 数据库 `MyPetStore`，不通过 HTTP 互调购买、补货等业务。

## 1. 文件选择

| 文件 | 内容 | 适用场景 |
| --- | --- | --- |
| `01-schema.sql` | 仅七张表结构，无业务数据 | 需要空库或自己准备数据时 |
| `02-schema-with-demo.sql` | 七张表结构 + 完整演示数据 | 实验验收、直接体验完整流程 |

**二选一，不要按编号依次执行。** 两个脚本都包含完整建表语句；重复执行会遇到“表已存在”错误。

## 2. 环境要求

- MySQL **8.0.16+**（推荐 8.0.28/8.4）。脚本使用 `utf8mb4_0900_ai_ci`、`CHECK` 约束、生成列，MySQL 5.7 及更早版本不支持。
- InnoDB，`utf8mb4`。
- 时区按 `Asia/Shanghai` 解释；脚本会执行 `SET SESSION time_zone = '+08:00'`。
- 数据库账号需有 `CREATE DATABASE`、`CREATE TABLE`、`INSERT` 权限。

## 3. 导入 02（推荐，一次完成结构与演示数据）

在空库环境执行：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < 02-schema-with-demo.sql
```

或在 MySQL 客户端中：

```sql
SOURCE D:/path/to/database/02-schema-with-demo.sql;
```

脚本会：

1. 创建数据库 `MyPetStore`（不存在时）；
2. 创建七张业务表；
3. 在一个事务中写入 4 个账号、15 个分类、60 个商品、100 条相册、10 条购物车、10 张订单、20 条明细；
4. 最后输出七张表的行数核对结果。

**不要加 `--force`**，不要跳过错误；脚本出错时应停止并检查目标库。

## 4. 导入 01（仅结构）

```bash
mysql -uroot -p --default-character-set=utf8mb4 < 01-schema.sql
```

空库不会包含教学账号和商品，需要自行准备数据，且必须满足表约束。

## 5. 预期结果

执行 02 后，最后一条核对查询应返回：

| 表 | 行数 |
| --- | ---: |
| `store_user` | 4 |
| `category` | 15 |
| `product` | 60 |
| `product_image` | 100 |
| `cart_item` | 10 |
| `sales_order` | 10 |
| `sales_order_item` | 20 |

并满足：54 个上架商品、6 个下架商品、3 个上架且零库存商品；`alice`、`bob` 各 5 张订单，覆盖五种状态；6 张已支付订单使用 `MOCK` 凭证。

## 6. 教学账号

| 用户名 | 角色 | 状态 | 显示名 |
| --- | --- | --- | --- |
| `alice` | CUSTOMER | ENABLED | 林同学 |
| `bob` | CUSTOMER | ENABLED | 陈同学 |
| `disabled` | CUSTOMER | DISABLED | 禁用教学账号 |
| `admin` | ADMIN | ENABLED | 商城管理员 |

统一密码：`PetStore123!`。密码以 BCrypt 散列保存，脚本不保存明文；应用使用 `BCryptPasswordEncoder.matches` 校验。

`alice`、`bob` 用于归属隔离；`disabled` 用于禁用检查；`admin` 只用于管理端。不要用商城教学账号去登录 MySQL；MySQL 账号与商城账号是两套凭据。

## 7. 应用连接配置

两个工程均读取环境变量：

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=MyPetStore
DB_USERNAME=root
DB_PASSWORD=
```

也可以直接修改各自 `src/main/resources/application-dev.yaml` 中的默认值，但不要提交真实密码。

## 8. 重置演示数据

仅限本地开发/验收环境：

```sql
DROP DATABASE MyPetStore;
```

然后重新执行 02。应用启动时 **不会** 自动建库、清库或重建数据；正常重启不会改变业务数据。

## 9. 常见错误

| 错误 | 原因 | 处理 |
| --- | --- | --- |
| `Unknown collation: 'utf8mb4_0900_ai_ci'` | MySQL 低于 8.0 | 升级到 MySQL 8 |
| `Table 'store_user' already exists` | 目标库已有表 | 确认库是否正确；需要重置时先 `DROP DATABASE` |
| `Cannot add or update a child row` | 外键引用的分类/商品不存在 | 检查是否只执行了部分脚本 |
| `Data too long` / 中文乱码 | 客户端字符集不是 utf8mb4 | 加 `--default-character-set=utf8mb4` |
| 账号无法登录 | 密码不是 `PetStore123!` 或账号被禁用 | 用 `admin`/`alice` 核对；禁用账号不能登录 |
| 时间相差 8 小时 | 数据库/连接时区不一致 | 使用脚本的 `+08:00`，JDBC 使用 `Asia/Shanghai` |

## 10. 数据边界说明

- 图片路径为站内静态资源相对路径，例如 `images/products/K9-BGL-03/1.jpg`；图片来自课程参考 UI，不表示真实商品品相。
- 订单明细保存商品 SKU、名称、封面、单价、数量、行金额快照；商品后续改名/改价/下架不会改写历史订单。
- 购物车只保存用户、商品、数量和版本，不保存可信价格，不预占库存。
- 订单/明细/库存/购物车清理在同一事务中完成；脚本中的种子订单只对未取消订单净扣库存。
