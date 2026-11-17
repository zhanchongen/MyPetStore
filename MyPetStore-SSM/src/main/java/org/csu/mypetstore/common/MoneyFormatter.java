package org.csu.mypetstore.common;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 模板统一人民币格式：¥ + 两位小数 + 千分位，例如 ¥3,800.00。
 *
 * <p>用 {@link Locale#ROOT} 固定小数点和千分位符号，避免不同 locale 下渲染成
 * “¥3.800,00” 这类结果；千分位与课程视频里的展示口径一致。</p>
 */
@Component("money")
public class MoneyFormatter {

    public String format(BigDecimal value) {
        if (value == null) {
            return "¥0.00";
        }
        return "¥" + String.format(Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }
}
