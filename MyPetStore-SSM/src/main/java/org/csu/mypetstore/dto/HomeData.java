package org.csu.mypetstore.dto;

import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.entity.Product;

import java.util.List;

/** 首页数据：一级分类 + 两个固定商品栏目。 */
public record HomeData(
        List<CategoryNode> categories,
        List<Product> newArrivals,
        List<Product> featured) {

    /**
     * 一级分类节点。只放数据库里真实存在的字段（名称、编码、二级分类），
     * 不再挂图标：表里没有图标字段，硬编码 emoji 属于伪造展示内容。
     */
    public record CategoryNode(Category category, List<Category> children) {
    }
}
