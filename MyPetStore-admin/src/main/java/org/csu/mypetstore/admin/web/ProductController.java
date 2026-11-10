package org.csu.mypetstore.admin.web;

import org.csu.mypetstore.admin.common.PageLinks;
import org.csu.mypetstore.admin.common.PageParams;
import org.csu.mypetstore.admin.domain.enums.ProductStatus;
import org.csu.mypetstore.admin.dto.AdminProductRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.entity.Product;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.form.ProductForm;
import org.csu.mypetstore.admin.form.ReplenishForm;
import org.csu.mypetstore.admin.service.CategoryAdminService;
import org.csu.mypetstore.admin.service.ProductAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductAdminService productService;
    private final CategoryAdminService categoryService;

    public ProductController(ProductAdminService productService, CategoryAdminService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long topCategoryId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String pageSize,
                       Model model) {
        PageParams params = PageParams.of(page, pageSize, 20);
        PageResult<AdminProductRow> result = productService.search(
                topCategoryId, categoryId, keyword, status, params.page(), params.pageSize());

        Map<String, String> linkParams = new HashMap<>();
        put(linkParams, "topCategoryId", topCategoryId);
        put(linkParams, "categoryId", categoryId);
        put(linkParams, "keyword", keyword);
        put(linkParams, "status", status);
        put(linkParams, "pageSize", pageSize);

        model.addAttribute("active", "products");
        model.addAttribute("pageResult", result);
        model.addAttribute("pageLinks", PageLinks.build("/products", linkParams, params.page(), (int) result.pages()));
        model.addAttribute("topCategoryId", topCategoryId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statusOptions", ProductStatus.values());
        addCategories(model);
        return "ProductList";
    }

    @GetMapping("/new")
    public String createPage(Model model) {
        ProductForm form = new ProductForm();
        form.setSortOrder(0);
        form.setStock(0);
        model.addAttribute("active", "products");
        model.addAttribute("mode", "create");
        model.addAttribute("pageTitle", "新增商品");
        model.addAttribute("formAction", "/products");
        model.addAttribute("form", form);
        addCategories(model);
        return "ProductForm";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ProductForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            Long id = productService.create(form);
            redirectAttributes.addFlashAttribute("message", "商品已创建，当前状态为下架。");
            return "redirect:/products/" + id + "/edit";
        } catch (BadRequestException | ConflictException ex) {
            model.addAttribute("active", "products");
            model.addAttribute("mode", "create");
            model.addAttribute("pageTitle", "新增商品");
            model.addAttribute("formAction", "/products");
            model.addAttribute("form", form);
            model.addAttribute("error", ex.getMessage());
            addCategories(model);
            return "ProductForm";
        }
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, Model model) {
        Product product = productService.getRequired(id);
        model.addAttribute("active", "products");
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "编辑商品");
        model.addAttribute("formAction", "/products/" + id);
        model.addAttribute("form", productService.toForm(product));
        model.addAttribute("product", product);
        addCategories(model);
        return "ProductForm";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") ProductForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            productService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "商品资料已更新。");
            return "redirect:/products";
        } catch (BadRequestException | ConflictException ex) {
            model.addAttribute("active", "products");
            model.addAttribute("mode", "edit");
            model.addAttribute("pageTitle", "编辑商品");
            model.addAttribute("formAction", "/products/" + id);
            model.addAttribute("form", form);
            model.addAttribute("product", productService.getRequired(id));
            model.addAttribute("error", ex.getMessage());
            addCategories(model);
            return "ProductForm";
        }
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam String status,
                               @RequestParam(required = false) Long version,
                               @RequestParam(required = false) String back,
                               RedirectAttributes redirectAttributes) {
        try {
            boolean changed = productService.changeStatus(id, status, version);
            if (changed) {
                redirectAttributes.addFlashAttribute("message",
                        "ON_SALE".equals(status) ? "商品已上架。" : "商品已下架。");
            } else {
                redirectAttributes.addFlashAttribute("message", "商品当前已是该状态，没有修改。");
            }
        } catch (BadRequestException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + safeBack(back, "/products");
    }

    @GetMapping("/{id}/replenish")
    public String replenishPage(@PathVariable Long id, Model model) {
        Product product = productService.getRequired(id);
        ReplenishForm form = new ReplenishForm();
        form.setId(product.getId());
        form.setProductName(product.getName());
        model.addAttribute("active", "products");
        model.addAttribute("form", form);
        model.addAttribute("product", product);
        return "ReplenishForm";
    }

    @PostMapping("/{id}/replenish")
    public String replenish(@PathVariable Long id,
                            @ModelAttribute("form") ReplenishForm form,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            int stock = productService.replenish(id, form.getQuantity());
            redirectAttributes.addFlashAttribute("message", "补货成功，当前库存为 " + stock + " 件。");
            return "redirect:/products/" + id + "/edit";
        } catch (BadRequestException | ConflictException ex) {
            Product product = productService.getRequired(id);
            form.setId(id);
            form.setProductName(product.getName());
            model.addAttribute("active", "products");
            model.addAttribute("form", form);
            model.addAttribute("product", product);
            model.addAttribute("error", ex.getMessage());
            return "ReplenishForm";
        }
    }

    private void addCategories(Model model) {
        model.addAttribute("topCategories", categoryService.listFirstLevel());
        model.addAttribute("secondCategories", categoryService.listSecondLevel());
    }

    private void put(Map<String, String> params, String key, Object value) {
        if (value != null) {
            params.put(key, String.valueOf(value));
        }
    }

    private String safeBack(String back, String fallback) {
        if (back == null || !back.startsWith("/") || back.startsWith("//")
                || back.contains("\\") || back.contains("\r") || back.contains("\n")) {
            return fallback;
        }
        return back;
    }
}
