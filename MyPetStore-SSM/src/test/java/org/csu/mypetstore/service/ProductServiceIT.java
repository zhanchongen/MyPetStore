package org.csu.mypetstore.service;

import org.csu.mypetstore.domain.enums.ProductSort;
import org.csu.mypetstore.domain.enums.ProductStatus;
import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.dto.HomeData;
import org.csu.mypetstore.dto.ProductCard;
import org.csu.mypetstore.dto.ProductDetailData;
import org.csu.mypetstore.dto.ProductSearchResult;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.ProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 首页栏目与商品查询的集成测试：连真实数据库、只读、事务回滚。
 *
 * <p>对应视频里的「新品、精选分别查询」与「商品查询规则」：
 * 新品最多 3 件、精选最多 4 件、两者都只取上架商品，且各自有明确排序。
 * 预期值由 Mapper 全量读取后在 Java 侧独立算出，不复用被测方法的实现。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductServiceIT {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductMapper productMapper;

    @Test
    @DisplayName("首页分类导航：5 个一级分类，各带真实二级分类")
    void homeCategories() {
        HomeData home = productService.home();
        assertThat(home.categories()).hasSize(5);
        assertThat(home.categories()).allSatisfy(node -> {
            assertThat(node.category().getParentId()).isNull();
            assertThat(node.children()).hasSize(2);
        });
    }

    @Test
    @DisplayName("首页新品：最多 3 件，全部上架，按 created_at、id 倒序")
    void homeNewArrivals() {
        HomeData home = productService.home();
        assertThat(home.newArrivals()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(home.newArrivals()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE));

        List<Product> all = productMapper.selectList(null);
        List<Long> expected = all.stream()
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE)
                .sorted((a, b) -> {
                    int byTime = b.getCreatedAt().compareTo(a.getCreatedAt());
                    return byTime != 0 ? byTime : Long.compare(b.getId(), a.getId());
                })
                .limit(3)
                .map(Product::getId)
                .toList();
        assertThat(home.newArrivals()).extracting(Product::getId).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("首页精选：最多 4 件，全部上架，按 sort_order、id 升序")
    void homeFeatured() {
        HomeData home = productService.home();
        assertThat(home.featured()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(home.featured()).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE));

        List<Product> all = productMapper.selectList(null);
        List<Long> expected = all.stream()
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE)
                .sorted((a, b) -> {
                    int bySort = Integer.compare(a.getSortOrder(), b.getSortOrder());
                    return bySort != 0 ? bySort : Long.compare(a.getId(), b.getId());
                })
                .limit(4)
                .map(Product::getId)
                .toList();
        assertThat(home.featured()).extracting(Product::getId).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("列表只返回上架商品：下架商品不会出现在分页结果里")
    void searchExcludesDraft() {
        Set<Long> draftIds = productMapper.selectList(null).stream()
                .filter(p -> p.getStatus() != ProductStatus.ON_SALE)
                .map(Product::getId)
                .collect(Collectors.toSet());
        assertThat(draftIds).as("种子数据里应存在下架商品用于验证过滤").isNotEmpty();

        ProductSearchResult result = productService.search(null, ProductType.ALL, ProductSort.DEFAULT, null, 1, 6);
        assertThat(result.page().records()).isNotEmpty();
        assertThat(result.page().records()).extracting(ProductCard::getId).doesNotContainAnyElementsOf(draftIds);
        assertThat(result.page().records()).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(ProductStatus.ON_SALE));
    }

    @Test
    @DisplayName("两级分类筛选：从二级分类进入时默认选中对应类型，且仍限定在该一级分类内")
    void searchBySecondLevelCategory() {
        // 101 = 犬类-宠物
        ProductSearchResult result = productService.search(101L, null, ProductSort.DEFAULT, null, 1, 6);
        assertThat(result.type()).isEqualTo(ProductType.PETS);
        assertThat(result.topCategory()).isNotNull();
        assertThat(result.topCategory().getId()).isEqualTo(1L);
        assertThat(result.secondCategory()).isNotNull();
        assertThat(result.secondCategory().getId()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("宠物");

        // 切换类型后仍在犬类范围内：只保留 101（宠物），不含 102（用品）
        ProductSearchResult switched = productService.search(101L, ProductType.PETS, ProductSort.DEFAULT, null, 1, 6);
        assertThat(switched.type()).isEqualTo(ProductType.PETS);
        assertThat(switched.page().records()).isNotEmpty();
    }

    @Test
    @DisplayName("商品详情：上架商品可取，下架商品抛 NotFoundException")
    void detailRespectsStatus() {
        ProductDetailData detail = productService.detail(1L);
        assertThat(detail.product().getSku()).isEqualTo("K9-BGL-03");
        assertThat(detail.topCategory()).isNotNull();
        assertThat(detail.category()).isNotNull();

        Product draft = productMapper.selectList(null).stream()
                .filter(p -> p.getStatus() != ProductStatus.ON_SALE)
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> productService.detail(draft.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> productService.detail(999_999L))
                .isInstanceOf(NotFoundException.class);
    }
}
