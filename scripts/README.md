# 验证脚本

本目录只放**可重复执行、只读或可安全回滚**的辅助脚本，不替代 `docs/验证记录.md` 中的人工验收步骤。

## `user_smoke.py`

用户端只读 smoke：欢迎页/首页/分类/详情/登录页、`/demo` 演示接口、alice 登录后的购物车/订单/支付/资料页，以及禁用账号登录拒绝。不写数据库。

```bash
python scripts/user_smoke.py
python scripts/user_smoke.py http://127.0.0.1:8080
```

## `admin_smoke.py`

管理端登录、CSRF、页面渲染与错误页 smoke 检查。只读，不会新增/修改业务数据（缺少 CSRF 的 POST 会被 403 拒绝）。

前置条件：

- 管理端已启动在 `http://127.0.0.1:8081`；
- 数据库已导入 `database/02-schema-with-demo.sql`；
- 使用演示账号 `admin / PetStore123!`。

```bash
python scripts/admin_smoke.py
python scripts/admin_smoke.py http://127.0.0.1:8081
```

输出每一页的 HTTP 状态、`<title>`、长度，以及是否残留 `th:`、`${`、`null`、`NaN`、Whitelabel 等异常标记。

## 数据库重置

重置脚本不做自动化，避免误删非演示库。按根目录 [数据库说明](../database/README.md) 执行 `DROP DATABASE MyPetStore;` 后重新导入 `02`。

## 用户端 E2E

用户端购物车、结算、订单、支付/取消的完整操作见 [验证记录](../docs/验证记录.md)；其中包含可复制的 HTTP 操作顺序与预期结果。
