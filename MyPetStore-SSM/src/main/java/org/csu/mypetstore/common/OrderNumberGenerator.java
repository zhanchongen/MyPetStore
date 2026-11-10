package org.csu.mypetstore.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 服务端生成唯一订单号/支付凭证，不使用最大编号加一。 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String orderNo() {
        return "ORD" + TS.format(LocalDateTime.now()) + random(6);
    }

    public String paymentNo() {
        return "MOCK" + TS.format(LocalDateTime.now()) + random(6);
    }

    private String random(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
