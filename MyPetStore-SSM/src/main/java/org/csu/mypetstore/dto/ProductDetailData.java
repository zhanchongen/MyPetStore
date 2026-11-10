package org.csu.mypetstore.dto;

import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.entity.ProductImage;

import java.util.List;

/** 商品详情页数据。 */
public record ProductDetailData(
        Product product,
        Category topCategory,
        Category category,
        List<ProductImage> images) {
}
