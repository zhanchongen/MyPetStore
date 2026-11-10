package org.csu.mypetstore.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.csu.mypetstore.admin.entity.SalesOrder;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    /** 仅 PAID 可发货；条件更新保证重复/并发发货只有一次成功。 */
    @Update("UPDATE sales_order SET status = 'SHIPPED', shipped_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'PAID'")
    int ship(@Param("id") Long id);
}
