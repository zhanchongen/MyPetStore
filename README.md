# MyPetStore 实验一

《软件开发架构平台》实验一：使用 SSM / Spring Boot 开发 MyPetStore 宠物商城。
本仓库包含 **用户端** 与 **管理端** 两个可独立构建、独立运行的 Spring Boot 工程，以及共享数据库脚本、设计与验收文档。

> 需求基线：`MyPetStore-PRD.pdf`（V1.1 学生实施基线）。当实验指导书、原型与本仓库文档不一致时，以 PRD 为准；差异见 [指导书与 PRD 差异说明](docs/设计说明.md#指导书与-prd-差异说明)。

## 工程与技术栈

| 项目 | 说明 |
| --- | --- |
| 用户端 | `MyPetStore-SSM`，端口 `8080`，Cookie `PETSTORE_USER_SESSION` |
| 管理端 | `MyPetStore-admin`，端口 `8081`，Cookie `PETSTORE_ADMIN_SESSION` |
| 数据库 | MySQL 8.0.16+，库名 `MyPetStore`，七张业务表 |
| 后端 | Spring Boot 4.1.2、Spring MVC、Thymeleaf、MyBatis-Plus 3.5.18.1 |
| 运行环境 | JDK 17+、Maven 3.9+ |
| 测试 | JUnit 5、Mockito、MockMvc；数据库/浏览器流程为手工 E2E |

课程视频使用 JDK 27 与 Spring Boot 4；本工程使用同一代 Spring Boot 4.1.2，源码级别为 Java 17，可在 JDK 17/21/27 上运行。若课程环境强制 JDK 27，只需把两个 `pom.xml` 的 `java.version` 改为 `27` 并在 JDK 27 下构建。

## 目录结构

```text
.
├─ database/                 # 唯一的共享 SQL 来源（01 结构版 / 02 结构+演示数据）
├─ docs/                     # 设计说明、需求完成清单、验证记录、视频对照、提交记录
├─ scripts/                  # smoke 脚本、页面截图验收脚本、商品插画生成脚本
├─ MyPetStore-SSM/           # 用户端工程
└─ MyPetStore-admin/         # 管理端工程
```

## 快速开始

### 1. 准备数据库

要求 MySQL 8.0.16+。在空库环境执行 **02**（结构 + 演示数据），**不要**再执行 01：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < database/02-schema-with-demo.sql
```

详见 [database/README.md](database/README.md)。

### 2. 配置连接

两个工程均从环境变量读取数据库连接，默认值适用于本机 root 空密码：

| 变量 | 默认值 |
| --- | --- |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `MyPetStore` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | 空 |

PowerShell 示例：

```powershell
$env:DB_PORT = "3306"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "你的密码"
```

不要在仓库中提交真实数据库密码。

### 3. 启动两端

分别打开两个终端：

```bash
cd MyPetStore-SSM
mvn spring-boot:run
```

```bash
cd MyPetStore-admin
mvn spring-boot:run
```

| 端 | 地址 |
| --- | --- |
| 用户端 | http://localhost:8080 |
| 管理端 | http://localhost:8081 |

### 4. 教学账号

演示数据统一密码：`PetStore123!`

| 用户名 | 角色 | 状态 | 用途 |
| --- | --- | --- | --- |
| `alice` | CUSTOMER | ENABLED | 正常购物、订单、购物车 |
| `bob` | CUSTOMER | ENABLED | 归属隔离验证 |
| `disabled` | CUSTOMER | DISABLED | 禁用账号验证 |
| `admin` | ADMIN | ENABLED | 管理端登录 |

普通用户登录只接受启用的 `CUSTOMER`；管理端登录只接受启用的 `ADMIN`。

## 测试

```bash
# 用户端：单元 + MockMvc（不需要数据库）
cd MyPetStore-SSM
mvn test

# 管理端
cd MyPetStore-admin
mvn test
```

用户端 43 个、管理端 24 个测试，共 67 个，均不依赖数据库。数据库导入、两端启动、用户端购物/下单/支付/取消、管理端分类/商品/订单/用户操作的手工 E2E 步骤与结果见 [docs/验证记录.md](docs/验证记录.md)；`scripts/user_smoke.py` 与 `scripts/admin_smoke.py` 可重复执行两端只读 smoke。

## 视觉验收

页面观感是要交付的一部分，不能靠"写完没看过"。`scripts/capture-pages.mjs` 用系统自带的 Edge/Chrome 以无头模式（CDP，零第三方依赖）逐页整页截图，覆盖未登录、已登录、购物车、结算、订单、个人资料与管理端主要页面：

```bash
# 前提：MySQL、8080、8081 都已启动，桌面已安装 Edge 或 Chrome；Node.js >= 22
node scripts/capture-pages.mjs rendered
```

字体（Sora / Plus Jakarta Sans）已本地化到 `static/fonts/`，不依赖 Google Fonts；40 个演示商品的封面插画由 `scripts/generate_product_art.py` 生成，可重复运行。

## 功能状态

实验一 P0 全部实现；选做 P1：个人资料、修改密码、确认收货、管理端用户启停。
逐项需求与代码位置见 [docs/需求完成清单.md](docs/需求完成清单.md)，验收证据见 [docs/验证记录.md](docs/验证记录.md)。

## 文档

- [数据库说明](database/README.md)
- [设计说明](docs/设计说明.md)
- [需求完成清单](docs/需求完成清单.md)
- [验证记录](docs/验证记录.md)
- [视觉验收](docs/视觉验收.md)
- [视频步骤对照](docs/视频对照.md)
- [提交记录](docs/提交记录.md)

## 说明

- 本项目为课程教学项目，不涉及真实支付、真实物流、真实身份认证或生产部署。
- 模拟支付仅用于演示订单状态机，页面明确标注“教学模拟支付，不涉及真实资金”。
- 课程原始视频、PDF、DOC、UI 压缩包不进入仓库；`database/` 下为共享 SQL 的唯一副本。
