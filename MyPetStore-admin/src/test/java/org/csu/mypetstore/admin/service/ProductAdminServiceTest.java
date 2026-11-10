package org.csu.mypetstore.admin.service;

import org.csu.mypetstore.admin.domain.enums.ProductStatus;
import org.csu.mypetstore.admin.entity.Category;
import org.csu.mypetstore.admin.entity.Product;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.form.ProductForm;
import org.csu.mypetstore.admin.mapper.CategoryMapper;
import org.csu.mypetstore.admin.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    private ProductAdminService service;

    private final Category secondLevel = Category.builder().id(10L).parentId(1L).name("狗粮").build();

    @BeforeEach
    void setUp() {
        service = new ProductAdminService(productMapper, categoryMapper);
    }

    private ProductForm validForm() {
        ProductForm form = new ProductForm();
        form.setSku("TEST-ADM-1");
        form.setName("测试犬粮");
        form.setCategoryId(10L);
        form.setPrice(new BigDecimal("12.34"));
        form.setStock(10);
        form.setSummary("简介");
        form.setDescription("描述");
        form.setCoverImage("/images/products/K9-BGL-03/1.jpg");
        form.setSortOrder(0);
        return form;
    }

    @Test
    void createDefaultsToDraftAndNormalizesCoverPath() {
        when(categoryMapper.selectById(10L)).thenReturn(secondLevel);
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(99L);
            return 1;
        });

        Long id = service.create(validForm());

        assertThat(id).isEqualTo(99L);
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(captor.getValue().getVersion()).isZero();
        assertThat(captor.getValue().getCoverImage()).isEqualTo("images/products/K9-BGL-03/1.jpg");
    }

    @Test
    void duplicateSkuIsConflict() {
        when(categoryMapper.selectById(10L)).thenReturn(secondLevel);
        when(productMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(validForm()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SKU 已存在，必须全局唯一。");
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void priceWithMoreThanTwoDecimalsIsRejected() {
        when(categoryMapper.selectById(10L)).thenReturn(secondLevel);
        ProductForm form = validForm();
        form.setPrice(new BigDecimal("12.345"));

        assertThatThrownBy(() -> service.create(form))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("价格最多保留两位小数。");
    }

    @Test
    void categoryMustBeExistingSecondLevel() {
        when(categoryMapper.selectById(10L)).thenReturn(Category.builder().id(10L).name("一级分类").build());

        assertThatThrownBy(() -> service.create(validForm()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("商品只能归属到已存在的二级分类。");
    }

    @Test
    void updateRejectsStaleVersion() {
        when(productMapper.selectById(83L)).thenReturn(Product.builder().id(83L).version(3L).build());
        ProductForm form = validForm();
        form.setId(83L);
        form.setVersion(2L);

        assertThatThrownBy(() -> service.update(83L, form))
                .isInstanceOf(ConflictException.class)
                .hasMessage("商品已发生变化，请刷新后重试。");
        verify(productMapper, never()).updateDetails(any(), any());
    }

    @Test
    void updateUsesVersionedConditionalStatement() {
        Product existing = Product.builder()
                .id(83L)
                .version(3L)
                .status(ProductStatus.DRAFT)
                .build();
        when(productMapper.selectById(83L)).thenReturn(existing);
        when(categoryMapper.selectById(10L)).thenReturn(secondLevel);
        when(productMapper.updateDetails(any(), any())).thenReturn(1);
        ProductForm form = validForm();
        form.setId(83L);
        form.setVersion(3L);

        assertThatCode(() -> service.update(83L, form)).doesNotThrowAnyException();
        verify(productMapper).updateDetails(any(), org.mockito.ArgumentMatchers.eq(3L));
    }

    @Test
    void changeStatusRequiresVersionAndReturnsFalseForSameStatus() {
        Product draft = Product.builder()
                .id(83L)
                .categoryId(10L)
                .version(0L)
                .status(ProductStatus.DRAFT)
                .price(new BigDecimal("12.34"))
                .coverImage("images/products/K9-BGL-03/1.jpg")
                .build();
        when(productMapper.selectById(83L)).thenReturn(draft);

        assertThat(service.changeStatus(83L, "DRAFT", 0L)).isFalse();
        verify(productMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    void changeStatusToOnSaleUsesVersionedUpdate() {
        Product draft = Product.builder()
                .id(83L)
                .categoryId(10L)
                .version(8L)
                .status(ProductStatus.DRAFT)
                .price(new BigDecimal("12.34"))
                .coverImage("images/products/K9-BGL-03/1.jpg")
                .build();
        when(productMapper.selectById(83L)).thenReturn(draft);
        when(categoryMapper.selectById(10L)).thenReturn(secondLevel);
        when(productMapper.updateStatus(83L, "ON_SALE", 8L)).thenReturn(1);

        assertThat(service.changeStatus(83L, "ON_SALE", 8L)).isTrue();
        verify(productMapper).updateStatus(83L, "ON_SALE", 8L);
    }

    @Test
    void replenishUsesAtomicIncrementAndReturnsLatestStock() {
        when(productMapper.selectById(83L)).thenReturn(Product.builder().id(83L).stock(10).build(),
                Product.builder().id(83L).stock(13).build());
        when(productMapper.replenish(83L, 3)).thenReturn(1);

        assertThat(service.replenish(83L, 3)).isEqualTo(13);
        verify(productMapper).replenish(83L, 3);
    }

    @Test
    void replenishRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.replenish(83L, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("补货数量必须是正整数。");
    }
}
