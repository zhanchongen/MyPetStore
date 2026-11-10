package org.csu.mypetstore.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.csu.mypetstore.admin.dto.CategoryRow;
import org.csu.mypetstore.admin.entity.Category;
import org.csu.mypetstore.admin.entity.Product;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.form.CategoryForm;
import org.csu.mypetstore.admin.mapper.CategoryMapper;
import org.csu.mypetstore.admin.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 分类维护：只允许两级，code/父子关系创建后不可改，删除前检查子分类与商品引用。 */
@Service
public class CategoryAdminService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CategoryAdminService(CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    public List<Category> listAll() {
        return categoryMapper.selectList(new QueryWrapper<Category>()
                .orderByAsc("parent_id")
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public List<Category> listFirstLevel() {
        return categoryMapper.selectList(new QueryWrapper<Category>()
                .isNull("parent_id")
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public List<Category> listSecondLevel() {
        return categoryMapper.selectList(new QueryWrapper<Category>()
                .isNotNull("parent_id")
                .orderByAsc("parent_id")
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    public Category getRequired(Long id) {
        if (id == null) {
            throw new BadRequestException("分类编号不能为空。");
        }
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new NotFoundException("分类不存在。");
        }
        return category;
    }

    public List<CategoryRow> listRows() {
        List<Category> all = listAll();
        Map<Long, String> names = new HashMap<>();
        for (Category category : all) {
            names.put(category.getId(), category.getName());
        }
        List<CategoryRow> rows = new ArrayList<>();
        for (Category category : all) {
            long childCount = categoryMapper.selectCount(new QueryWrapper<Category>()
                    .eq("parent_id", category.getId()));
            long productCount = productMapper.selectCount(new QueryWrapper<Product>()
                    .eq("category_id", category.getId()));
            rows.add(new CategoryRow(category,
                    category.getParentId() == null ? "—" : names.getOrDefault(category.getParentId(), "—"),
                    childCount,
                    productCount));
        }
        return rows;
    }

    @Transactional
    public Long create(CategoryForm form) {
        String code = trim(form.getCode());
        String name = trim(form.getName());
        validateCode(code);
        validateName(name);
        int sortOrder = form.getSortOrder() == null ? 0 : form.getSortOrder();
        if (sortOrder < 0) {
            throw new BadRequestException("排序必须是非负整数。");
        }

        Long parentId = normalizeParentId(form.getParentId());
        if (parentId != null) {
            Category parent = getRequired(parentId);
            if (parent.getParentId() != null) {
                throw new BadRequestException("分类最多两级，不能在二级分类下继续新增。");
            }
        }
        if (categoryMapper.selectCount(new QueryWrapper<Category>().eq("code", code)) > 0) {
            throw new ConflictException("分类编码已存在，编码必须全局唯一。");
        }
        if (nameExists(name, parentId, null)) {
            throw new ConflictException("同一父分类下已存在同名分类。");
        }

        LocalDateTime now = LocalDateTime.now();
        Category category = Category.builder()
                .code(code)
                .parentId(parentId)
                .name(name)
                .sortOrder(sortOrder)
                .createdAt(now)
                .updatedAt(now)
                .build();
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional
    public void update(Long id, CategoryForm form) {
        Category existing = getRequired(id);
        String name = trim(form.getName());
        validateName(name);
        int sortOrder = form.getSortOrder() == null ? 0 : form.getSortOrder();
        if (sortOrder < 0) {
            throw new BadRequestException("排序必须是非负整数。");
        }
        if (nameExists(name, existing.getParentId(), existing.getId())) {
            throw new ConflictException("同一父分类下已存在同名分类。");
        }
        Category update = Category.builder()
                .id(existing.getId())
                .name(name)
                .sortOrder(sortOrder)
                .updatedAt(LocalDateTime.now())
                .build();
        categoryMapper.updateById(update);
    }

    @Transactional
    public void delete(Long id) {
        getRequired(id);
        long children = categoryMapper.selectCount(new QueryWrapper<Category>().eq("parent_id", id));
        if (children > 0) {
            throw new ConflictException("该分类下还有子分类，不能删除。");
        }
        long products = productMapper.selectCount(new QueryWrapper<Product>().eq("category_id", id));
        if (products > 0) {
            throw new ConflictException("该分类仍被 " + products + " 个商品引用（含下架商品），不能删除。");
        }
        if (categoryMapper.deleteById(id) == 0) {
            throw new NotFoundException("分类不存在。");
        }
    }

    public CategoryForm toForm(Category category) {
        CategoryForm form = new CategoryForm();
        form.setId(category.getId());
        form.setCode(category.getCode());
        form.setName(category.getName());
        form.setParentId(category.getParentId());
        form.setSortOrder(category.getSortOrder());
        return form;
    }

    private void validateCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BadRequestException("分类编码不能为空。");
        }
        if (code.length() > 32) {
            throw new BadRequestException("分类编码不能超过 32 个字符。");
        }
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("分类名称不能为空。");
        }
        if (name.length() > 50) {
            throw new BadRequestException("分类名称不能超过 50 个字符。");
        }
    }

    private boolean nameExists(String name, Long parentId, Long excludeId) {
        QueryWrapper<Category> wrapper = new QueryWrapper<Category>().eq("name", name);
        if (parentId == null) {
            wrapper.isNull("parent_id");
        } else {
            wrapper.eq("parent_id", parentId);
        }
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return categoryMapper.selectCount(wrapper) > 0;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null || parentId == 0L ? null : parentId;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
