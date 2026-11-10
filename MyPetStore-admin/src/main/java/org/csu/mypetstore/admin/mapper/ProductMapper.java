package org.csu.mypetstore.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.csu.mypetstore.admin.entity.Product;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** 编辑资料：SKU、库存、状态、版本由服务层与条件更新保护。 */
    @Update("UPDATE product SET name = #{p.name}, category_id = #{p.categoryId}, "
            + "price = #{p.price}, summary = #{p.summary}, description = #{p.description}, "
            + "cover_image = #{p.coverImage}, sort_order = #{p.sortOrder}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE id = #{p.id} AND version = #{version}")
    int updateDetails(@Param("p") Product product, @Param("version") Long version);

    /** 上下架：必须带旧版本号，防止旧页面覆盖新变化。 */
    @Update("UPDATE product SET status = #{status}, version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND version = #{version}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("version") Long version);

    /** 增量补货：在数据库最新库存上原子增加，不接收页面旧库存绝对值。 */
    @Update("UPDATE product SET stock = stock + #{quantity}, version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id}")
    int replenish(@Param("id") Long id, @Param("quantity") Integer quantity);
}
