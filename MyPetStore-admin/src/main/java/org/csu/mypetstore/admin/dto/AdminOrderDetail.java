package org.csu.mypetstore.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.mypetstore.admin.entity.SalesOrder;
import org.csu.mypetstore.admin.entity.SalesOrderItem;
import org.csu.mypetstore.admin.entity.StoreUser;

import java.util.List;

/** 订单详情快照；管理端只读订单金额/明细/支付凭证。 */
@Data
@AllArgsConstructor
public class AdminOrderDetail {

    private SalesOrder order;
    private StoreUser user;
    private List<SalesOrderItem> items;
}
