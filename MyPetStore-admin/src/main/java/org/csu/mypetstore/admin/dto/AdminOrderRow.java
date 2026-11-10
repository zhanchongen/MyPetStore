package org.csu.mypetstore.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.mypetstore.admin.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 全站订单列表行。 */
@Data
@AllArgsConstructor
public class AdminOrderRow {

    private Long id;
    private String orderNo;
    private String username;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String receiverName;
    private LocalDateTime createdAt;
}
