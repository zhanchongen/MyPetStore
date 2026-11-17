package org.csu.mypetstore.web;

import org.csu.mypetstore.common.PageLinks;
import org.csu.mypetstore.common.PageParams;
import org.csu.mypetstore.domain.enums.ProductSort;
import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.dto.ProductDetailData;
import org.csu.mypetstore.dto.ProductSearchResult;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/category")
    public String category(@RequestParam(required = false) String categoryId,
                           @RequestParam(required = false) String productType,
                           @RequestParam(required = false) String sort,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String page,
                           @RequestParam(required = false) String pageSize,
                           Model model) {
        Long parsedCategoryId = parseCategoryId(categoryId);
        ProductType type = ProductType.from(productType);
        if (type == null) {
            throw new BadRequestException("商品类型参数不正确。");
        }
        ProductSort parsedSort = ProductSort.from(sort);
        if (parsedSort == null) {
            throw new BadRequestException("排序参数不正确。");
        }
        PageParams pageParams = PageParams.of(page, pageSize, 6);

        ProductSearchResult result = productService.search(
                parsedCategoryId, type, parsedSort, keyword, pageParams.page(), pageParams.pageSize());

        Map<String, String> filterParams = new LinkedHashMap<>();
        if (result.topCategoryId() != null) {
            filterParams.put("categoryId", String.valueOf(result.topCategoryId()));
        }
        if (result.type() != ProductType.ALL) {
            filterParams.put("productType", result.type().name());
        }
        if (result.sort() != ProductSort.DEFAULT) {
            filterParams.put("sort", result.sort().name());
        }
        if (result.keyword() != null) {
            filterParams.put("keyword", result.keyword());
        }
        if (pageParams.pageSize() != 6) {
            filterParams.put("pageSize", String.valueOf(pageParams.pageSize()));
        }

        model.addAttribute("search", result);
        model.addAttribute("pageResult", result.page());
        model.addAttribute("pageLinks", PageLinks.build(
                "/category", filterParams, (int) result.page().page(), (int) result.page().pages()));
        model.addAttribute("topCategories", result.topCategories());
        model.addAttribute("secondCategories", result.secondCategories());
        model.addAttribute("topCategory", result.topCategory());
        model.addAttribute("secondCategory", result.secondCategory());
        model.addAttribute("productType", result.type());
        model.addAttribute("typeOptions", ProductType.values());
        model.addAttribute("sort", result.sort());
        model.addAttribute("sortOptions", ProductSort.values());
        model.addAttribute("keyword", result.keyword() == null ? "" : result.keyword());
        model.addAttribute("title", result.title());
        model.addAttribute("filterParams", filterParams);
        return "Category";
    }

    /**
     * 商品详情。视频约定用 {@code /product?id=<真实ID>}；为兼容已有链接，
     * {@code /product/{id}} 也映射到同一个方法，两者都只认数据库商品 ID，
     * 不再使用原型里的 {@code ?sku=}。
     */
    @GetMapping({"/product", "/product/{id}"})
    public String product(@PathVariable(name = "id", required = false) String pathId,
                          @RequestParam(name = "id", required = false) String queryId,
                          Model model) {
        Long id = parseProductId(pathId != null ? pathId : queryId);
        ProductDetailData detail = productService.detail(id);
        model.addAttribute("detail", detail);
        model.addAttribute("product", detail.product());
        model.addAttribute("topCategory", detail.topCategory());
        model.addAttribute("category", detail.category());
        model.addAttribute("images", detail.images());
        model.addAttribute("cover", productService.coverOf(detail.product()));
        return "Product";
    }

    private Long parseProductId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("缺少商品 ID。");
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("商品 ID 不正确。");
        }
    }

    private Long parseCategoryId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new BadRequestException("分类参数格式不正确。");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("分类参数格式不正确。");
        }
    }
}
