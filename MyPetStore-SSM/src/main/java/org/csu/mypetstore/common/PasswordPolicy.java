package org.csu.mypetstore.common;

import org.csu.mypetstore.exception.BadRequestException;

import java.nio.charset.StandardCharsets;

/** 新密码规则（PRD 6.7）：8—32 字符、≤72 UTF-8 字节、至少两类字符、无空白。 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null) {
            throw new BadRequestException("新密码不能为空。");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 32) {
            throw new BadRequestException("新密码长度必须为 8—32 个字符。");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("新密码的 UTF-8 编码不能超过 72 字节。");
        }

        boolean letter = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); ) {
            int codePoint = password.codePointAt(i);
            if (Character.isWhitespace(codePoint)) {
                throw new BadRequestException("新密码不能包含空白字符。");
            }
            if (Character.isLetter(codePoint)) {
                letter = true;
            } else if (Character.isDigit(codePoint)) {
                digit = true;
            } else {
                symbol = true;
            }
            i += Character.charCount(codePoint);
        }

        int categories = (letter ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
        if (categories < 2) {
            throw new BadRequestException("新密码至少包含字母、数字、符号中的两类。");
        }
    }
}
