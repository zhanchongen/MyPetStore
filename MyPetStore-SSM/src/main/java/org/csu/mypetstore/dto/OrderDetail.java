package org.csu.mypetstore.dto;

import org.csu.mypetstore.entity.SalesOrder;
import org.csu.mypetstore.entity.SalesOrderItem;

import java.util.List;

/** 订单详情：主表 + 明细快照。 */
public record OrderDetail(SalesOrder order, List<SalesOrderItem> items) {
}
