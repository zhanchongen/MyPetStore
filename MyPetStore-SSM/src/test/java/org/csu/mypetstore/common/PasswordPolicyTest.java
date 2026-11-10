package org.csu.mypetstore.common;

import org.csu.mypetstore.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordWithThreeCharacterClasses() {
        assertThatCode(() -> PasswordPolicy.validate("PetStore123!")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码不能为空。");
    }

    @Test
    void rejectsTooShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码长度必须为 8—32 个字符。");
    }

    @Test
    void rejectsTooLongPassword() {
        String value = "Aa1!" + "a".repeat(29);
        assertThatThrownBy(() -> PasswordPolicy.validate(value))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码长度必须为 8—32 个字符。");
    }

    @Test
    void rejectsUtf8Over72BytesEvenWhenCharacterCountIsValid() {
        String value = "密码".repeat(14);
        assertThatThrownBy(() -> PasswordPolicy.validate(value))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码的 UTF-8 编码不能超过 72 字节。");
    }

    @Test
    void rejectsWhitespace() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcd 1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码不能包含空白字符。");
    }

    @Test
    void rejectsSingleCharacterClass() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefgh"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("新密码至少包含字母、数字、符号中的两类。");
    }
}
