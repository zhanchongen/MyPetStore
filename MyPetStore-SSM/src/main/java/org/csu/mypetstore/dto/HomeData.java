package org.csu.mypetstore.dto;

import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.entity.Product;

import java.util.List;

/** 首页数据：一级分类 + 两个固定商品栏目。 */
public record HomeData(
        List<CategoryNode> categories,
        List<Product> newArrivals,
        List<Product> featured) {

    public record CategoryNode(Category category, List<Category> children, String icon) {
    }
}
