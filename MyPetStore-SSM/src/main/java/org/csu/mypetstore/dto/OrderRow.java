package org.csu.mypetstore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 我的订单列表行。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRow {

    private Long id;
    private String orderNo;
    private org.csu.mypetstore.domain.enums.OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private Integer itemCount;
    /** 订单首件商品的封面快照，用于列表缩略图；历史订单不随商品改图而变化。 */
    private String coverImage;
}
