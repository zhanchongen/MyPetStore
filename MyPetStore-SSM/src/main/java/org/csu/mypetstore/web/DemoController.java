package org.csu.mypetstore.web;

import org.csu.mypetstore.dto.UserReadResult;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实验一“工程初始化与基础验证”演示接口，只在 dev profile 下注册。
 * 不对应正式业务页面，也不返回任何密码字段。
 */
@RestController
@RequestMapping("/demo")
@Profile("dev")
public class DemoController {

    private final StoreUserMapper userMapper;

    public DemoController(StoreUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 验证 Spring MVC 路由与 UTF-8 文本响应。 */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public String demo() {
        return "Spring MVC 初始化成功！";
    }

    /** 验证 Mapper -> JSON 查询链路，只返回展示字段。 */
    @GetMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserReadResult> user(@RequestParam(name = "username", defaultValue = "alice") String username) {
        StoreUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(new UserReadResult(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus() == null ? null : user.getStatus().name()));
    }
}
