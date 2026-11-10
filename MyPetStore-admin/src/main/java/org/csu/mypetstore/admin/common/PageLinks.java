package org.csu.mypetstore.admin.common;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 分页链接：保留筛选条件，支持上一页/下一页/首末页/页码。 */
public record PageLinks(
        List<PageLink> pages,
        String previousUrl,
        String nextUrl,
        String firstUrl,
        String lastUrl) {

    public record PageLink(int number, String url, boolean current) {
    }

    public static PageLinks build(String basePath, Map<String, String> params, int page, int pages) {
        int safePages = Math.max(1, pages);
        int current = Math.min(Math.max(1, page), safePages);

        List<PageLink> links = new ArrayList<>();
        int start = Math.max(1, current - 2);
        int end = Math.min(safePages, start + 4);
        start = Math.max(1, end - 4);
        for (int i = start; i <= end; i++) {
            links.add(new PageLink(i, url(basePath, params, i), i == current));
        }

        return new PageLinks(
                links,
                current > 1 ? url(basePath, params, current - 1) : null,
                current < safePages ? url(basePath, params, current + 1) : null,
                url(basePath, params, 1),
                url(basePath, params, safePages));
    }

    private static String url(String basePath, Map<String, String> params, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);
        if (params != null) {
            params.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    builder.queryParam(key, value);
                }
            });
        }
        builder.queryParam("page", page);
        return builder.build().encode().toUriString();
    }
}
