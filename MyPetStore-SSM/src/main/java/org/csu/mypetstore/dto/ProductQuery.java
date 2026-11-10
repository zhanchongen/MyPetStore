package org.csu.mypetstore.dto;

import java.util.List;

/** 商品列表查询条件，categoryIds 由 Service 解析后传入 Mapper。 */
public record ProductQuery(
        Long topCategoryId,
        List<Long> categoryIds,
        String keyword,
        org.csu.mypetstore.domain.enums.ProductSort sort,
        int page,
        int pageSize) {
}
