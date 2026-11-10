package org.csu.mypetstore.dto;

import org.csu.mypetstore.domain.enums.ProductSort;
import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.entity.Category;

import java.util.List;

/** 商品列表页的归一化查询结果。 */
public record ProductSearchResult(
        PageResult<ProductCard> page,
        List<Category> topCategories,
        List<Category> secondCategories,
        Long topCategoryId,
        Category topCategory,
        Category secondCategory,
        ProductType type,
        ProductSort sort,
        String keyword,
        String title) {
}
