package org.csu.mypetstore.common;

import org.csu.mypetstore.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageParamsTest {

    @Test
    void usesDefaultsForBlankInput() {
        PageParams params = PageParams.of(null, "  ", 6);
        assertThat(params.page()).isEqualTo(1);
        assertThat(params.pageSize()).isEqualTo(6);
    }

    @Test
    void normalizesPageBelowOne() {
        assertThat(PageParams.of("0", "12", 6)).isEqualTo(new PageParams(1, 12));
        assertThat(PageParams.of("-9", null, 10)).isEqualTo(new PageParams(1, 10));
    }

    @Test
    void acceptsBoundaries() {
        assertThat(PageParams.of("3", "1", 6)).isEqualTo(new PageParams(3, 1));
        assertThat(PageParams.of("3", "50", 6)).isEqualTo(new PageParams(3, 50));
    }

    @Test
    void rejectsNonNumericPage() {
        assertThatThrownBy(() -> PageParams.of("abc", "6", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("页码必须是整数。");
    }

    @Test
    void rejectsNonNumericSize() {
        assertThatThrownBy(() -> PageParams.of("1", "x", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("每页数量必须是整数。");
    }

    @Test
    void rejectsSizeOutsideOneToFifty() {
        assertThatThrownBy(() -> PageParams.of("1", "0", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("每页数量必须在 1—50 之间。");
        assertThatThrownBy(() -> PageParams.of("1", "51", 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("每页数量必须在 1—50 之间。");
    }
}
