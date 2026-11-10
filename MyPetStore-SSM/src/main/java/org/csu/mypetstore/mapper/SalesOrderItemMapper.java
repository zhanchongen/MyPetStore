package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.csu.mypetstore.entity.SalesOrderItem;

import java.util.List;

public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {

    List<SalesOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    int insertBatch(@Param("items") List<SalesOrderItem> items);
}
