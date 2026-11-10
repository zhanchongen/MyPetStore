package org.csu.mypetstore.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.mypetstore.admin.entity.SalesOrderItem;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {
}
