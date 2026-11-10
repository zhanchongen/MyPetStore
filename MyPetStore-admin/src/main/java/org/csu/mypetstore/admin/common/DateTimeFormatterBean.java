package org.csu.mypetstore.admin.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 模板统一时间格式，避免依赖额外的 Thymeleaf 时间扩展。 */
@Component("fmt")
public class DateTimeFormatterBean {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String dt(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }
}
