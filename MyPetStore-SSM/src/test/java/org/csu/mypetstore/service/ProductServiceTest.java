package org.csu.mypetstore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.domain.enums.ProductSort;
import org.csu.mypetstore.domain.enums.ProductStatus;
import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.dto.PageResult;
import org.csu.mypetstore.dto.ProductCard;
import org.csu.mypetstore.dto.ProductDetailData;
import org.csu.mypetstore.dto.ProductQuery;
import org.csu.mypetstore.dto.ProductSearchResult;
import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.CategoryMapper;
import org.csu.mypetstore.mapper.ProductImageMapper;
import org.csu.mypetstore.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductImageMapper productImageMapper;

    private ProductService productService;

    private final Category petsTop = Category.builder().id(1L).code("DOGS_PETS").name("宠物").build();
    private final Category petChild = Category.builder().id(10L).code("DOG_PETS").name("狗粮").parentId(1L).build();
    private final Category supplyChild = Category.builder().id(11L).code("CAT_SUPPLIES").name("猫用品").parentId(1L).build();
    private final Category unmappedChild = Category.builder().id(12L).code("DOG_FOOD").name("狗粮").parentId(1L).build();

    @BeforeEach
    void setUp() {
        productService = new ProductService(productMapper, categoryMapper, productImageMapper,
                new CategoryService(categoryMapper));
    }

    private void stubPage() {
        Page<ProductCard> page = new Page<>(1, 6);
        page.setRecords(List.of());
        page.setTotal(0);
        when(productMapper.selectUserPage(any(), any())).thenReturn(page);
    }

    @Test
    void searchWithoutCategoryUsesAllSecondLevelCategoriesAndSanitizesKeyword() {
        when(categoryMapper.selectAllOrdered()).thenReturn(List.of(petsTop, petChild, supplyChild));
        stubPage();

        ProductSearchResult result = productService.search(null, ProductType.ALL, ProductSort.DEFAULT,
                "  狗粮  ", 1, 6);

        ArgumentCaptor<ProductQuery> query = ArgumentCaptor.forClass(ProductQuery.class);
        verify(productMapper).selectUserPage(any(), query.capture());
        assertThat(query.getValue().categoryIds()).containsExactly(10L, 11L);
        assertThat(query.getValue().keyword()).isEqualTo("狗粮");
        assertThat(query.getValue().sort()).isEqualTo(ProductSort.DEFAULT);
        assertThat(result.title()).isEqualTo("全部商品");
        assertThat(result.page()).isEqualTo(new PageResult<ProductCard>(List.of(), 0, 1, 6));
    }

    @Test
    void searchByTopCategoryFiltersChildrenByType() {
        when(categoryMapper.selectAllOrdered()).thenReturn(List.of(petsTop, petChild, supplyChild));
        stubPage();

        ProductSearchResult result = productService.search(1L, ProductType.PETS, ProductSort.PRICE_ASC,
                null, 1, 6);

        ArgumentCaptor<ProductQuery> query = ArgumentCaptor.forClass(ProductQuery.class);
        verify(productMapper).selectUserPage(any(), query.capture());
        assertThat(query.getValue().topCategoryId()).isEqualTo(1L);
        assertThat(query.getValue().categoryIds()).containsExactly(10L);
        assertThat(result.topCategory()).isEqualTo(petsTop);
        assertThat(result.title()).isEqualTo("宠物");
        assertThat(result.type()).isEqualTo(ProductType.PETS);
    }

    @Test
    void searchByUnmappedSecondLevelUsesExactCategory() {
        when(categoryMapper.selectAllOrdered()).thenReturn(List.of(petsTop, petChild, supplyChild, unmappedChild));
        stubPage();

        ProductSearchResult result = productService.search(12L, ProductType.ALL, ProductSort.NEWEST,
                null, 1, 6);

        ArgumentCaptor<ProductQuery> query = ArgumentCaptor.forClass(ProductQuery.class);
        verify(productMapper).selectUserPage(any(), query.capture());
        assertThat(query.getValue().categoryIds()).containsExactly(12L);
        assertThat(result.secondCategory()).isEqualTo(unmappedChild);
        assertThat(result.title()).isEqualTo("狗粮");
    }

    @Test
    void unknownCategoryIsNotFound() {
        when(categoryMapper.selectAllOrdered()).thenReturn(List.of(petsTop, petChild));

        assertThatThrownBy(() -> productService.search(999L, ProductType.ALL, ProductSort.DEFAULT, null, 1, 6))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("分类不可用。");
        verify(productMapper, never()).selectUserPage(any(), any());
    }

    @Test
    void tooLongKeywordIsBadRequest() {
        when(categoryMapper.selectAllOrdered()).thenReturn(List.of(petsTop, petChild));

        assertThatThrownBy(() -> productService.search(null, ProductType.ALL, ProductSort.DEFAULT,
                "a".repeat(121), 1, 6))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("搜索关键字最多 120 个字符。");
        verify(productMapper, never()).selectUserPage(any(), any());
    }

    @Test
    void detailLoadsOnSaleProductWithCategoryAndImages() {
        Product product = Product.builder()
                .id(5L)
                .categoryId(10L)
                .name("成犬粮")
                .price(new BigDecimal("109.00"))
                .stock(8)
                .status(ProductStatus.ON_SALE)
                .build();
        when(productMapper.selectById(5L)).thenReturn(product);
        when(categoryMapper.selectById(10L)).thenReturn(petChild);
        when(categoryMapper.selectById(1L)).thenReturn(petsTop);
        when(productImageMapper.selectByProductId(5L)).thenReturn(List.of());

        ProductDetailData detail = productService.detail(5L);

        assertThat(detail.product()).isSameAs(product);
        assertThat(detail.category()).isEqualTo(petChild);
        assertThat(detail.topCategory()).isEqualTo(petsTop);
    }

    @Test
    void detailRejectsDraftProduct() {
        when(productMapper.selectById(5L)).thenReturn(Product.builder()
                .id(5L)
                .categoryId(10L)
                .status(ProductStatus.DRAFT)
                .build());

        assertThatThrownBy(() -> productService.detail(5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("商品不存在或已下架。");
    }
}
