package org.csu.mypetstore.admin.web;

import org.csu.mypetstore.admin.dto.CategoryRow;
import org.csu.mypetstore.admin.entity.Category;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.form.CategoryForm;
import org.csu.mypetstore.admin.service.CategoryAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryAdminService categoryService;

    public CategoryController(CategoryAdminService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        List<CategoryRow> rows = categoryService.listRows();
        model.addAttribute("active", "categories");
        model.addAttribute("rows", rows);
        return "CategoryList";
    }

    @GetMapping("/new")
    public String createPage(Model model) {
        CategoryForm form = new CategoryForm();
        form.setSortOrder(0);
        model.addAttribute("active", "categories");
        model.addAttribute("mode", "create");
        model.addAttribute("pageTitle", "新增分类");
        model.addAttribute("formAction", "/categories");
        model.addAttribute("form", form);
        model.addAttribute("parents", categoryService.listFirstLevel());
        return "CategoryForm";
    }

    @PostMapping
    public String create(@ModelAttribute("form") CategoryForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            categoryService.create(form);
            redirectAttributes.addFlashAttribute("message", "分类已创建。");
            return "redirect:/categories";
        } catch (BadRequestException | ConflictException ex) {
            model.addAttribute("active", "categories");
            model.addAttribute("mode", "create");
            model.addAttribute("pageTitle", "新增分类");
            model.addAttribute("formAction", "/categories");
            model.addAttribute("form", form);
            model.addAttribute("parents", categoryService.listFirstLevel());
            model.addAttribute("error", ex.getMessage());
            return "CategoryForm";
        }
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id, Model model) {
        Category category = categoryService.getRequired(id);
        CategoryForm form = categoryService.toForm(category);
        model.addAttribute("active", "categories");
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "编辑分类");
        model.addAttribute("formAction", "/categories/" + id);
        model.addAttribute("form", form);
        model.addAttribute("category", category);
        model.addAttribute("parents", categoryService.listFirstLevel());
        model.addAttribute("parentName", parentName(category));
        return "CategoryForm";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") CategoryForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            categoryService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "分类已更新。");
            return "redirect:/categories";
        } catch (BadRequestException | ConflictException ex) {
            Category category = categoryService.getRequired(id);
            form.setId(id);
            form.setCode(category.getCode());
            form.setParentId(category.getParentId());
            model.addAttribute("active", "categories");
            model.addAttribute("mode", "edit");
            model.addAttribute("pageTitle", "编辑分类");
            model.addAttribute("formAction", "/categories/" + id);
            model.addAttribute("form", form);
            model.addAttribute("category", category);
            model.addAttribute("parents", categoryService.listFirstLevel());
            model.addAttribute("parentName", parentName(category));
            model.addAttribute("error", ex.getMessage());
            return "CategoryForm";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("message", "分类已删除。");
        } catch (ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/categories";
    }

    private String parentName(Category category) {
        if (category.getParentId() == null) {
            return "—";
        }
        for (Category parent : categoryService.listFirstLevel()) {
            if (parent.getId().equals(category.getParentId())) {
                return parent.getName();
            }
        }
        return "—";
    }
}
