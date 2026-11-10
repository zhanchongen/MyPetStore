# -*- coding: utf-8 -*-
"""管理端 smoke 检查：登录、鉴权、CSRF、页面渲染与错误页。

只读脚本：故意缺少 CSRF 的 POST 会被后端 403 拒绝，不产生业务数据。
用法：python scripts/admin_smoke.py [base_url]
"""
import http.cookiejar
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8081").rstrip("/")
TITLE = re.compile(r"<title>([^<]+)</title>")
CSRF = re.compile(r'name="_csrf" value="([^"]+)"')
BAD_MARKERS = ("th:", "${", "[[", "null", "NaN", "Whitelabel")


def make_opener():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


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

print("=== 未登录访问控制台 ===")
op = make_opener()
status, html, url = get(op, "/dashboard")
print("status", status, "final", url, "title", title(html))

print("=== CUSTOMER 登录被拒绝 ===")
op = make_opener()
status, html, url = login(op, "alice", "PetStore123!")
print("status", status, "url", url, "rejected", "没有管理权限" in html)
status, html, url = get(op, "/dashboard")
print("dashboard after customer login ->", status, url)

print("=== ADMIN 登录 ===")
op = make_opener()
status, html, url = login(op, "admin", "PetStore123!")
print("status", status, "url", url, "title", title(html))

print("=== 页面渲染 ===")
for path in ["/dashboard", "/categories", "/categories/new", "/categories/1/edit",
             "/products", "/products/new", "/products/1/edit", "/products/1/replenish",
             "/orders", "/orders/1", "/users"]:
    status, html, _ = get(op, path)
    bad = [marker for marker in BAD_MARKERS if marker in html]
    print(path, status, title(html), "len", len(html), "bad", bad)

print("=== 缺少 CSRF 的写请求 ===")
try:
    status, html, _ = post(op, "/categories", {"code": "NO-CSRF", "name": "无令牌"})
    print("status", status, "title", title(html))
except urllib.error.HTTPError as error:
    body = error.read().decode("utf-8", "replace")
    print("status", error.code, "title", title(body), "has-403-page", "无权访问" in body)

print("=== 错误页 ===")
for path in ["/categories/999999/edit", "/orders?status=BOGUS"]:
    try:
        status, html, _ = get(op, path)
        print(path, status, title(html))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        print(path, error.code, title(body))

print("done")
