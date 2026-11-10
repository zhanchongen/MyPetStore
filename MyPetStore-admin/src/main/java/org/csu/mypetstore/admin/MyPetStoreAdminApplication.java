package org.csu.mypetstore.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MyPetStore 管理端入口：独立工程、8081 端口、共享 MyPetStore 数据库。 */
@SpringBootApplication
@MapperScan("org.csu.mypetstore.admin.mapper")
public class MyPetStoreAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyPetStoreAdminApplication.class, args);
    }
}
