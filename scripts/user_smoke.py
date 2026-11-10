# -*- coding: utf-8 -*-
"""用户端只读 smoke：公共页、演示接口、登录与受保护页面。

不写数据库；登录使用演示账号 alice。用法：python scripts/user_smoke.py [base_url]
"""
import http.cookiejar
import re
import sys
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")
TITLE = re.compile(r"<title>([^<]+)</title>")
CSRF = re.compile(r'name="_csrf" value="([^"]+)"')
BAD_MARKERS = ("th:", "${", "[[", "null", "NaN", "Whitelabel")


def opener():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def get(op, path):
    with op.open(BASE + path, timeout=15) as response:
        return response.status, response.read().decode("utf-8", "replace"), response.geturl()


def post(op, path, data):
    body = urllib.parse.urlencode(data).encode("utf-8")
    request = urllib.request.Request(BASE + path, data=body, method="POST")
    with op.open(request, timeout=15) as response:
        return response.status, response.read().decode("utf-8", "replace"), response.geturl()


def login(op, username, password):
    _, html, _ = get(op, "/login")
    match = CSRF.search(html)
    assert match, "登录页缺少 CSRF token"
    return post(op, "/login", {"_csrf": match.group(1), "username": username, "password": password})


def title(html):
    match = TITLE.search(html)
    return match.group(1) if match else ""


print("base:", BASE)
op = opener()
print("=== 公共页 ===")
for path in ["/", "/main", "/category", "/category?categoryId=1&productType=PETS&sort=PRICE_ASC",
             "/product/1", "/login"]:
    status, html, _ = get(op, path)
    bad = [marker for marker in BAD_MARKERS if marker in html]
    print(path, status, title(html), "bad", bad)

print("=== 演示接口 ===")
status, text, _ = get(op, "/demo")
print("/demo", status, text)
status, text, _ = get(op, "/demo/user?username=alice")
print("/demo/user", status, text)

print("=== 登录 ===")
status, html, url = login(op, "alice", "PetStore123!")
print("alice", status, url, title(html))

print("=== 受保护页 ===")
for path in ["/cart", "/orders", "/orders?status=PENDING_PAYMENT", "/orders/1", "/payment/1", "/profile", "/profile/password"]:
    status, html, url = get(op, path)
    print(path, status, url, title(html))

print("=== 禁用账号 ===")
op2 = opener()
status, html, url = login(op2, "disabled", "PetStore123!")
print("disabled", status, url, "rejected", "账号或密码错误" in html)
print("done")
