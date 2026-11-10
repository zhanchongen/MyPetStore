package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.mypetstore.entity.Category;

import java.util.List;

public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT * FROM category ORDER BY sort_order, id")
    List<Category> selectAllOrdered();

    @Select("SELECT * FROM category WHERE code = #{code}")
    Category selectByCode(@Param("code") String code);

    @Select("SELECT * FROM category WHERE parent_id = #{parentId} ORDER BY sort_order, id")
    List<Category> selectByParentId(@Param("parentId") Long parentId);

    @Select("SELECT COUNT(*) FROM category WHERE parent_id = #{parentId}")
    int countChildren(@Param("parentId") Long parentId);

    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId}")
    int countProducts(@Param("categoryId") Long categoryId);
}
