package org.csu.mypetstore.common;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 模板统一人民币格式：¥ + 两位小数。 */
@Component("money")
public class MoneyFormatter {

    public String format(BigDecimal value) {
        if (value == null) {
            return "¥0.00";
        }
        return "¥" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
