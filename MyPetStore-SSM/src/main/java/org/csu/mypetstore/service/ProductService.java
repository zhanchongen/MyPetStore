package org.csu.mypetstore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.domain.enums.ProductSort;
import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.dto.HomeData;
import org.csu.mypetstore.dto.PageResult;
import org.csu.mypetstore.dto.ProductCard;
import org.csu.mypetstore.dto.ProductDetailData;
import org.csu.mypetstore.dto.ProductQuery;
import org.csu.mypetstore.dto.ProductSearchResult;
import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.entity.ProductImage;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.CategoryMapper;
import org.csu.mypetstore.mapper.ProductImageMapper;
import org.csu.mypetstore.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryService categoryService;

    public ProductService(ProductMapper productMapper,
                          CategoryMapper categoryMapper,
                          ProductImageMapper productImageMapper,
                          CategoryService categoryService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.productImageMapper = productImageMapper;
        this.categoryService = categoryService;
    }

    public HomeData home() {
        List<Category> all = categoryService.all();
        Map<Long, List<Category>> children = new LinkedHashMap<>();
        List<Category> topLevel = new ArrayList<>();
        for (Category category : all) {
            if (category.getParentId() == null) {
                topLevel.add(category);
            } else {
                children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
            }
        }
        List<HomeData.CategoryNode> nodes = topLevel.stream()
                .map(top -> new HomeData.CategoryNode(top, children.getOrDefault(top.getId(), List.of()),
                        categoryService.iconOf(top)))
                .toList();
        return new HomeData(nodes, productMapper.selectHomeNew(3), productMapper.selectHomeFeatured(4));
    }

    public ProductSearchResult search(Long categoryId,
                                      ProductType requestedType,
                                      ProductSort requestedSort,
                                      String keyword,
                                      int page,
                                      int pageSize) {
        List<Category> all = categoryService.all();
        Map<Long, Category> byId = new LinkedHashMap<>();
        for (Category category : all) {
            byId.put(category.getId(), category);
        }
        List<Category> topLevel = all.stream().filter(c -> c.getParentId() == null).toList();

        Category top = null;
        Category second = null;
        ProductType type = requestedType == null ? ProductType.ALL : requestedType;
        boolean exactSecondLevel = false;

        if (categoryId != null) {
            Category selected = byId.get(categoryId);
            if (selected == null) {
                throw new NotFoundException("分类不可用。");
            }
            if (selected.getParentId() == null) {
                top = selected;
            } else {
                second = selected;
                top = byId.get(selected.getParentId());
                ProductType mapped = categoryService.typeOf(selected);
                if (mapped != null) {
                    type = mapped;
                } else {
                    exactSecondLevel = true;
                    type = requestedType == null ? ProductType.ALL : requestedType;
                }
            }
        }

        List<Long> categoryIds = new ArrayList<>();
        if (exactSecondLevel) {
            categoryIds.add(second.getId());
        } else if (top != null) {
            for (Category child : all) {
                if (top.getId().equals(child.getParentId())) {
                    ProductType childType = categoryService.typeOf(child);
                    if (type == ProductType.ALL || childType == type) {
                        categoryIds.add(child.getId());
                    }
                }
            }
        } else {
            for (Category category : all) {
                if (category.getParentId() != null) {
                    ProductType childType = categoryService.typeOf(category);
                    if (type == ProductType.ALL || childType == type) {
                        categoryIds.add(category.getId());
                    }
                }
            }
        }
        if (categoryIds.isEmpty()) {
            categoryIds.add(-1L);
        }

        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 120) {
            throw new BadRequestException("搜索关键字最多 120 个字符。");
        }
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }

        ProductSort sort = requestedSort == null ? ProductSort.DEFAULT : requestedSort;
        Page<ProductCard> queryPage = new Page<>(page, pageSize);
        ProductQuery query = new ProductQuery(
                top == null ? null : top.getId(),
                categoryIds,
                normalizedKeyword,
                sort,
                page,
                pageSize);
        var resultPage = productMapper.selectUserPage(queryPage, query);
        PageResult<ProductCard> pageResult = new PageResult<>(
                resultPage.getRecords(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());

        String title;
        if (second != null) {
            title = second.getName();
        } else if (top != null) {
            title = top.getName();
        } else {
            title = "全部商品";
        }

        List<Category> secondLevel = new ArrayList<>();
        if (top != null) {
            for (Category child : all) {
                if (top.getId().equals(child.getParentId())) {
                    secondLevel.add(child);
                }
            }
        }

        return new ProductSearchResult(
                pageResult,
                topLevel,
                secondLevel,
                top == null ? null : top.getId(),
                top,
                second,
                type,
                sort,
                normalizedKeyword,
                title);
    }

    public ProductDetailData detail(Long id) {
        Product product = id == null ? null : productMapper.selectById(id);
        if (product == null || !product.isOnSale()) {
            throw new NotFoundException("商品不存在或已下架。");
        }
        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category == null || category.getParentId() == null) {
            throw new NotFoundException("商品分类不可用。");
        }
        Category top = categoryMapper.selectById(category.getParentId());
        if (top == null) {
            throw new NotFoundException("商品分类不可用。");
        }
        List<ProductImage> images = productImageMapper.selectByProductId(product.getId());
        return new ProductDetailData(product, top, category, images);
    }

    public String coverOf(Product product) {
        return product.getCoverImage() == null || product.getCoverImage().isBlank()
                ? "images/logo.png"
                : product.getCoverImage();
    }
}
