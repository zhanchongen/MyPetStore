package org.csu.mypetstore.service;

import org.csu.mypetstore.domain.enums.ProductType;
import org.csu.mypetstore.entity.Category;
import org.csu.mypetstore.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分类导航的集成测试：连真实数据库、只读、事务回滚。
 *
 * <p>对应视频里的「分类导航 Service 与测试」：一次查询取全部分类，
 * 再在内存里组织成一级 + 二级，不对每个一级分类单独查一次。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CategoryServiceIT {

    @Autowired
    private CategoryService categoryService;

    @Test
    @DisplayName("all 一次查出全部 15 条分类，按 sort_order、id 升序")
    void allReturnsAllCategories() {
        List<Category> all = categoryService.all();
        assertThat(all).hasSize(15);
        assertThat(all.get(0).getParentId()).isNull();
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i - 1).getSortOrder()).isLessThanOrEqualTo(all.get(i).getSortOrder());
        }
    }

    @Test
    @DisplayName("childrenByParent 在内存里分组：5 个一级分类，各挂 2 个二级分类")
    void childrenByParent() {
        Map<Long, List<Category>> grouped = categoryService.childrenByParent();
        assertThat(grouped).hasSize(5);
        assertThat(grouped.values()).allSatisfy(children -> assertThat(children).hasSize(2));
        assertThat(grouped.keySet()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("typeOf 依据二级分类编码判定宠物/用品，一级分类返回 null")
    void typeOf() {
        Category pets = categoryService.require(101L);
        Category supplies = categoryService.require(102L);
        Category top = categoryService.require(1L);

        assertThat(categoryService.typeOf(pets)).isEqualTo(ProductType.PETS);
        assertThat(categoryService.typeOf(supplies)).isEqualTo(ProductType.SUPPLIES);
        assertThat(categoryService.typeOf(top)).isNull();
        assertThat(categoryService.typeOf(null)).isNull();
    }

    @Test
    @DisplayName("require 未命中分类时抛 NotFoundException，而不是返回 null")
    void requireMissingThrows() {
        assertThatThrownBy(() -> categoryService.require(999_999L))
                .isInstanceOf(NotFoundException.class);
    }
}
