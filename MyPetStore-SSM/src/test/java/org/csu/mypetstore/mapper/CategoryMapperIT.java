package org.csu.mypetstore.mapper;

import org.csu.mypetstore.entity.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * category 表映射集成测试：连真实数据库、只读、事务回滚。
 *
 * <p>和 {@code *Test} 的区别：这类测试依赖真实的 MySQL 与演示数据，
 * 由 {@code mvn test} 默认不快（Surefire 默认只匹配 *Test），需要显式指定
 * {@code -Dtest=CategoryMapperIT} 或在 CI 里单独跑。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CategoryMapperIT {

    /** 种子数据中的二级分类：犬类-宠物，id=101，父分类 id=1。 */
    private static final Long SECOND_LEVEL_ID = 101L;
    /** 种子数据中的一级分类：犬类，id=1，parent_id 为空。 */
    private static final Long TOP_LEVEL_ID = 1L;

    @Autowired
    private CategoryMapper categoryMapper;

    @Test
    @DisplayName("selectById 读取二级分类：parent_id、code、sort_order 映射正确")
    void selectSecondLevel() {
        Category category = categoryMapper.selectById(SECOND_LEVEL_ID);
        assertThat(category).isNotNull();
        assertThat(category.getCode()).isEqualTo("DOGS_PETS");
        assertThat(category.getParentId()).isEqualTo(TOP_LEVEL_ID);
        assertThat(category.getSortOrder()).isNotNull();
        // DATETIME 列有数据库默认值，应能映射成 LocalDateTime
        assertThat(category.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("selectById 读取一级分类：parent_id 为 null，说明是顶层")
    void selectTopLevel() {
        Category category = categoryMapper.selectById(TOP_LEVEL_ID);
        assertThat(category).isNotNull();
        assertThat(category.getCode()).isEqualTo("DOGS");
        assertThat(category.getParentId()).isNull();
    }

    @Test
    @DisplayName("selectById 未命中主键：返回 null，不抛异常")
    void selectMissingReturnsNull() {
        assertThat(categoryMapper.selectById(999_999L)).isNull();
    }

    @Test
    @DisplayName("selectAllOrdered 按 sort_order、id 升序返回全部 15 条分类")
    void selectAllOrdered() {
        List<Category> all = categoryMapper.selectAllOrdered();
        assertThat(all).hasSize(15);
        assertThat(all.get(0).getParentId()).isNull();
        for (int i = 1; i < all.size(); i++) {
            Category prev = all.get(i - 1);
            Category cur = all.get(i);
            assertThat(prev.getSortOrder()).isLessThanOrEqualTo(cur.getSortOrder());
        }
    }

    @Test
    @DisplayName("selectByCode / selectByParentId / countChildren 覆盖两级分类查询")
    void queryByCodeAndParent() {
        Category top = categoryMapper.selectByCode("DOGS");
        assertThat(top).isNotNull();
        assertThat(top.getName()).isEqualTo("犬类");

        List<Category> children = categoryMapper.selectByParentId(TOP_LEVEL_ID);
        assertThat(children).hasSize(2);
        assertThat(children).extracting(Category::getCode)
                .containsExactly("DOGS_PETS", "DOGS_SUPPLIES");

        assertThat(categoryMapper.countChildren(TOP_LEVEL_ID)).isEqualTo(2);
        assertThat(categoryMapper.countProducts(TOP_LEVEL_ID)).isZero();
    }
}
