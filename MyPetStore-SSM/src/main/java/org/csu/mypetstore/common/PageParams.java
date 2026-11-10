package org.csu.mypetstore.common;

import org.csu.mypetstore.exception.BadRequestException;

/** 页码/页大小解析：格式错误 400，页码 &lt;1 归一，页大小 1—50。 */
public record PageParams(int page, int pageSize) {

    public static PageParams of(String pageRaw, String sizeRaw, int defaultSize) {
        int page = 1;
        if (pageRaw != null && !pageRaw.isBlank()) {
            try {
                page = Integer.parseInt(pageRaw.trim());
            } catch (NumberFormatException ex) {
                throw new BadRequestException("页码必须是整数。");
            }
            if (page < 1) {
                page = 1;
            }
        }

        int pageSize = defaultSize;
        if (sizeRaw != null && !sizeRaw.isBlank()) {
            try {
                pageSize = Integer.parseInt(sizeRaw.trim());
            } catch (NumberFormatException ex) {
                throw new BadRequestException("每页数量必须是整数。");
            }
            if (pageSize < 1 || pageSize > 50) {
                throw new BadRequestException("每页数量必须在 1—50 之间。");
            }
        }
        return new PageParams(page, pageSize);
    }
}
