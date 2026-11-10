package org.csu.mypetstore.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageLinksTest {

    @Test
    void keepsFiltersAndBuildsFivePageWindow() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("categoryId", "5");
        params.put("keyword", "狗粮");
        params.put("sort", "");

        PageLinks links = PageLinks.build("/category", params, 3, 10);

        assertThat(links.pages()).extracting(PageLinks.PageLink::number).containsExactly(1, 2, 3, 4, 5);
        assertThat(links.pages()).filteredOn(PageLinks.PageLink::current)
                .extracting(PageLinks.PageLink::number).containsExactly(3);
        assertThat(links.previousUrl()).contains("categoryId=5").contains("keyword=").contains("page=2");
        assertThat(links.nextUrl()).contains("page=4");
        assertThat(links.firstUrl()).contains("page=1");
        assertThat(links.lastUrl()).contains("page=10");
        assertThat(links.pages().get(0).url()).doesNotContain("sort=");
    }

    @Test
    void clampsCurrentPageAndHandlesSinglePage() {
        PageLinks links = PageLinks.build("/products", Map.of(), 99, 0);

        assertThat(links.pages()).hasSize(1);
        assertThat(links.pages().get(0).number()).isEqualTo(1);
        assertThat(links.pages().get(0).current()).isTrue();
        assertThat(links.previousUrl()).isNull();
        assertThat(links.nextUrl()).isNull();
        assertThat(links.firstUrl()).isEqualTo("/products?page=1");
        assertThat(links.lastUrl()).isEqualTo("/products?page=1");
    }
}
