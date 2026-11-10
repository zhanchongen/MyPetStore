package org.csu.mypetstore.admin.service;

import org.csu.mypetstore.admin.entity.Category;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.form.CategoryForm;
import org.csu.mypetstore.admin.mapper.CategoryMapper;
import org.csu.mypetstore.admin.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryAdminServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductMapper productMapper;

    private CategoryAdminService service;

    @BeforeEach
    void setUp() {
        service = new CategoryAdminService(categoryMapper, productMapper);
    }

    private CategoryForm form(Long parentId) {
        CategoryForm form = new CategoryForm();
        form.setCode("TEST-CODE");
        form.setName("测试分类");
        form.setParentId(parentId);
        form.setSortOrder(0);
        return form;
    }

    @Test
    void duplicateCodeIsConflict() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(form(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("分类编码已存在，编码必须全局唯一。");
    }

    @Test
    void thirdLevelCategoryIsRejected() {
        when(categoryMapper.selectById(2L)).thenReturn(Category.builder()
                .id(2L)
                .parentId(1L)
                .name("二级分类")
                .build());

        assertThatThrownBy(() -> service.create(form(2L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("分类最多两级，不能在二级分类下继续新增。");
    }

    @Test
    void deleteRejectsCategoryReferencedByProducts() {
        when(categoryMapper.selectById(10L)).thenReturn(Category.builder().id(10L).name("狗粮").build());
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any())).thenReturn(6L);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("该分类仍被 6 个商品引用（含下架商品），不能删除。");
    }

    @Test
    void deleteRejectsCategoryWithChildren() {
        when(categoryMapper.selectById(1L)).thenReturn(Category.builder().id(1L).name("宠物").build());
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("该分类下还有子分类，不能删除。");
    }
}
