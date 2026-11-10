package org.csu.mypetstore.service;

import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private static final Map<String, String> ICONS = Map.of(
            "DOGS", "🐕",
            "CATS", "🐈",
            "FISH", "🐠",
            "BIRDS", "🦜",
            "REPTILES", "🦎");

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> all() {
        return categoryMapper.selectAllOrdered();
    }

    public List<Category> topLevel() {
        return all().stream().filter(c -> c.getParentId() == null).toList();
    }

    public Map<Long, List<Category>> childrenByParent() {
        Map<Long, List<Category>> result = new LinkedHashMap<>();
        for (Category category : all()) {
            if (category.getParentId() != null) {
                result.computeIfAbsent(category.getParentId(), key -> new java.util.ArrayList<>()).add(category);
            }
        }
        return result;
    }

    public Category require(Long id) {
        Category category = id == null ? null : categoryMapper.selectById(id);
        if (category == null) {
            throw new NotFoundException("分类不可用。");
        }
        return category;
    }

    public Category parentOf(Category child) {
        if (child == null || child.getParentId() == null) {
            return null;
        }
        return categoryMapper.selectById(child.getParentId());
    }

    public ProductType typeOf(Category category) {
        if (category == null) {
            return null;
        }
        if ("宠物".equals(category.getName()) || category.getCode().endsWith("_PETS")) {
            return ProductType.PETS;
        }
        if ("用品".equals(category.getName()) || category.getCode().endsWith("_SUPPLIES")) {
            return ProductType.SUPPLIES;
        }
        return null;
    }

    public String iconOf(Category top) {
        return top == null ? "🐾" : ICONS.getOrDefault(top.getCode(), "🐾");
    }
}
