package org.csu.mypetstore.mapper;

import org.csu.mypetstore.domain.enums.ProductStatus;
import org.csu.mypetstore.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * product 表映射与首页查询的集成测试：连真实数据库、只读、事务回滚。
 *
 * <p>对应视频里的「实体、Mapper 与字段验证」和「新品/精选分别查询」两个小任务。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductMapperIT {

    /** 种子数据中的商品：比格犬，id=1，归属二级分类 101（宠物·犬类）。 */
    private static final Long SEED_PRODUCT_ID = 1L;

    @Autowired
    private ProductMapper productMapper;

    @Test
    @DisplayName("selectById 读取商品：价格、库存、状态、封面与时间字段映射正确")
    void selectSeedProduct() {
        Product product = productMapper.selectById(SEED_PRODUCT_ID);
        assertThat(product).isNotNull();
        assertThat(product.getSku()).isEqualTo("K9-BGL-03");
        assertThat(product.getCategoryId()).isEqualTo(101L);
        assertThat(product.getPrice()).isNotNull();
        assertThat(product.getStock()).isNotNull();
        assertThat(product.getStatus()).isInstanceOf(ProductStatus.class);
        assertThat(product.getCoverImage()).isNotBlank();
        assertThat(product.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("首页新品：最多 3 条，且都是上架商品")
    void selectHomeNew() {
        List<Product> products = productMapper.selectHomeNew(3);
        assertThat(products).hasSizeLessThanOrEqualTo(3).isNotEmpty();
        assertThat(products).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE));
    }

    @Test
    @DisplayName("首页精选：最多 4 条，按 sort_order、id 升序，且都是上架商品")
    void selectHomeFeatured() {
        List<Product> products = productMapper.selectHomeFeatured(4);
        assertThat(products).hasSizeLessThanOrEqualTo(4).isNotEmpty();
        assertThat(products).allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE));
        for (int i = 1; i < products.size(); i++) {
            Product prev = products.get(i - 1);
            Product cur = products.get(i);
            assertThat(prev.getSortOrder()).isLessThanOrEqualTo(cur.getSortOrder());
        }
    }

    @Test
    @DisplayName("零库存商品仍能被读到：库存为 0 不等于下架")
    void zeroStockStillSelectable() {
        Product soldOut = productMapper.selectList(null).stream()
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE && p.getStock() != null && p.getStock() == 0)
                .findFirst()
                .orElse(null);
        assertThat(soldOut).as("种子数据里应存在“上架但零库存”的商品").isNotNull();
        assertThat(productMapper.selectById(soldOut.getId())).isNotNull();
    }
}
