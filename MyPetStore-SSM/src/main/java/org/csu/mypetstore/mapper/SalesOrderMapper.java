package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.csu.mypetstore.domain.enums.OrderStatus;
import org.csu.mypetstore.dto.OrderRow;
import org.csu.mypetstore.entity.SalesOrder;

import java.time.LocalDateTime;

public interface SalesOrderMapper extends BaseMapper<SalesOrder> {

    IPage<OrderRow> selectUserPage(IPage<OrderRow> page,
                                   @Param("userId") Long userId,
                                   @Param("status") OrderStatus status,
                                   @Param("orderNo") String orderNo);

    SalesOrder selectByIdForUpdate(@Param("id") Long id);

    int updateToPaid(@Param("id") Long id,
                     @Param("paymentNo") String paymentNo,
                     @Param("paidAt") LocalDateTime paidAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int updateToCancelled(@Param("id") Long id,
                          @Param("cancelledAt") LocalDateTime cancelledAt,
                          @Param("updatedAt") LocalDateTime updatedAt);

    int updateToShipped(@Param("id") Long id,
                        @Param("shippedAt") LocalDateTime shippedAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    int updateToCompleted(@Param("id") Long id,
                          @Param("completedAt") LocalDateTime completedAt,
                          @Param("updatedAt") LocalDateTime updatedAt);
}
