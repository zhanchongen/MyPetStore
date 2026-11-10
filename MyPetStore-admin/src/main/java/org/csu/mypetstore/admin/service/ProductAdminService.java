package org.csu.mypetstore.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.admin.domain.enums.ProductStatus;
import org.csu.mypetstore.admin.dto.AdminProductRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.entity.Category;
import org.csu.mypetstore.admin.entity.Product;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.form.ProductForm;
import org.csu.mypetstore.admin.mapper.CategoryMapper;
import org.csu.mypetstore.admin.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 商品维护：SKU 与库存编辑只读，上下架/补货使用条件更新或原子增量。 */
@Service
public class ProductAdminService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public ProductAdminService(ProductMapper productMapper, CategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
    }

    public PageResult<AdminProductRow> search(Long topCategoryId,
                                               Long categoryId,
                                               String keyword,
                                               String statusRaw,
                                               int page,
                                               int pageSize) {
        ProductStatus status = parseStatus(statusRaw);
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.length() > 120) {
            throw new BadRequestException("商品名称关键词不能超过 120 个字符。");
        }

        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        } else if (topCategoryId != null) {
            wrapper.apply("category_id IN (SELECT id FROM category WHERE parent_id = {0})", topCategoryId);
        }
        if (StringUtils.hasText(kw)) {
            wrapper.like("name", kw);
        }
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        wrapper.orderByAsc("sort_order").orderByDesc("id");

        Page<Product> result = productMapper.selectPage(new Page<>(page, pageSize), wrapper);
        Map<Long, String> categoryNames = new HashMap<>();
        for (Category category : categoryMapper.selectList(null)) {
            categoryNames.put(category.getId(), category.getName());
        }
        List<AdminProductRow> rows = new ArrayList<>();
        for (Product product : result.getRecords()) {
            rows.add(toRow(product, categoryNames.getOrDefault(product.getCategoryId(), "—")));
        }
        return new PageResult<>(rows, result.getTotal(), page, pageSize);
    }

    public Product getRequired(Long id) {
        if (id == null) {
            throw new BadRequestException("商品编号不能为空。");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new NotFoundException("商品不存在。");
        }
        return product;
    }

    public ProductForm toForm(Product product) {
        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setSku(product.getSku());
        form.setName(product.getName());
        form.setCategoryId(product.getCategoryId());
        form.setPrice(product.getPrice());
        form.setStock(product.getStock());
        form.setSummary(product.getSummary());
        form.setDescription(product.getDescription());
        form.setCoverImage(product.getCoverImage());
        form.setSortOrder(product.getSortOrder());
        form.setVersion(product.getVersion());
        return form;
    }

    @Transactional
    public Long create(ProductForm form) {
        String sku = trim(form.getSku());
        String name = trim(form.getName());
        validateSku(sku);
        validateName(name);
        Long categoryId = validateCategory(form.getCategoryId());
        BigDecimal price = validatePrice(form.getPrice());
        int stock = form.getStock() == null ? 0 : form.getStock();
        if (stock < 0) {
            throw new BadRequestException("初始库存必须是非负整数。");
        }
        String summary = validateSummary(form.getSummary());
        String description = trim(form.getDescription());
        String cover = validateCover(form.getCoverImage());
        int sortOrder = form.getSortOrder() == null ? 0 : form.getSortOrder();
        if (sortOrder < 0) {
            throw new BadRequestException("排序必须是非负整数。");
        }
        if (productMapper.selectCount(new QueryWrapper<Product>().eq("sku", sku)) > 0) {
            throw new ConflictException("SKU 已存在，必须全局唯一。");
        }

        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .sku(sku)
                .categoryId(categoryId)
                .name(name)
                .summary(summary)
                .description(description)
                .coverImage(cover)
                .price(price)
                .stock(stock)
                .status(ProductStatus.DRAFT)
                .sortOrder(sortOrder)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        productMapper.insert(product);
        return product.getId();
    }

    @Transactional
    public void update(Long id, ProductForm form) {
        Product existing = getRequired(id);
        if (form.getVersion() == null) {
            throw new BadRequestException("缺少商品版本号，请刷新页面后重试。");
        }
        if (!form.getVersion().equals(existing.getVersion())) {
            throw new ConflictException("商品已发生变化，请刷新后重试。");
        }
        String name = trim(form.getName());
        validateName(name);
        Long categoryId = validateCategory(form.getCategoryId());
        BigDecimal price = validatePrice(form.getPrice());
        String summary = validateSummary(form.getSummary());
        String description = trim(form.getDescription());
        String cover = validateCover(form.getCoverImage());
        int sortOrder = form.getSortOrder() == null ? 0 : form.getSortOrder();
        if (sortOrder < 0) {
            throw new BadRequestException("排序必须是非负整数。");
        }

        Product update = Product.builder()
                .id(existing.getId())
                .name(name)
                .categoryId(categoryId)
                .price(price)
                .summary(summary)
                .description(description)
                .coverImage(cover)
                .sortOrder(sortOrder)
                .build();
        if (productMapper.updateDetails(update, form.getVersion()) == 0) {
            throw new ConflictException("商品已发生变化，请刷新后重试。");
        }
    }

    /**
     * 显式上下架；必须带旧版本号。返回 false 表示目标状态与当前一致，没有修改。
     */
    @Transactional
    public boolean changeStatus(Long id, String statusRaw, Long version) {
        ProductStatus target = parseStatus(statusRaw);
        if (target == null) {
            throw new BadRequestException("请选择上架或下架状态。");
        }
        if (version == null) {
            throw new BadRequestException("缺少商品版本号，请刷新页面后重试。");
        }
        Product product = getRequired(id);
        if (product.getStatus() == target) {
            return false;
        }
        if (target == ProductStatus.ON_SALE) {
            validateForOnSale(product);
        }
        if (productMapper.updateStatus(id, target.name(), version) == 0) {
            throw new ConflictException("商品已发生变化，请刷新后重试。");
        }
        return true;
    }

    /** 原子增量补货；返回补货后的最新库存。 */
    @Transactional
    public int replenish(Long id, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("补货数量必须是正整数。");
        }
        if (quantity > 1_000_000) {
            throw new BadRequestException("单次补货数量过大。");
        }
        getRequired(id);
        if (productMapper.replenish(id, quantity) == 0) {
            throw new NotFoundException("商品不存在。");
        }
        Product latest = productMapper.selectById(id);
        if (latest == null) {
            throw new NotFoundException("商品不存在。");
        }
        return latest.getStock() == null ? 0 : latest.getStock();
    }

    public ProductStatus parseStatus(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return ProductStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("未知的商品状态。");
        }
    }

    private AdminProductRow toRow(Product product, String categoryName) {
        return new AdminProductRow(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategoryId(),
                categoryName,
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getSortOrder(),
                product.getVersion(),
                product.getCoverImage());
    }

    private void validateSku(String sku) {
        if (!StringUtils.hasText(sku)) {
            throw new BadRequestException("SKU 不能为空。");
        }
        if (sku.length() > 40) {
            throw new BadRequestException("SKU 不能超过 40 个字符。");
        }
    }

    private void validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("商品名称不能为空。");
        }
        if (name.length() > 120) {
            throw new BadRequestException("商品名称不能超过 120 个字符。");
        }
    }

    private Long validateCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("必须选择商品分类。");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getParentId() == null) {
            throw new BadRequestException("商品只能归属到已存在的二级分类。");
        }
        return categoryId;
    }

    private BigDecimal validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("价格必须大于 0。");
        }
        if (price.scale() > 2) {
            throw new BadRequestException("价格最多保留两位小数。");
        }
        return price;
    }

    private String validateSummary(String summary) {
        String value = summary == null ? "" : summary.trim();
        if (value.length() > 500) {
            throw new BadRequestException("商品简介不能超过 500 个字符。");
        }
        return value;
    }

    private String validateCover(String coverImage) {
        String value = trim(coverImage);
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("封面图片路径不能为空。");
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.length() > 255) {
            throw new BadRequestException("封面图片路径不能超过 255 个字符。");
        }
        if (!value.startsWith("images/")) {
            throw new BadRequestException("封面必须使用站内 images/ 下的静态资源路径。");
        }
        return value;
    }

    private void validateForOnSale(Product product) {
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("商品价格无效，不能上架。");
        }
        Category category = categoryMapper.selectById(product.getCategoryId());
        if (category == null || category.getParentId() == null) {
            throw new ConflictException("商品必须归属到有效的二级分类后才能上架。");
        }
        if (!StringUtils.hasText(product.getCoverImage())) {
            throw new ConflictException("商品缺少封面图，不能上架。");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
