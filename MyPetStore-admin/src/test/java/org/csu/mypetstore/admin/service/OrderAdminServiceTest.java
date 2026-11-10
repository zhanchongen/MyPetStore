package org.csu.mypetstore.admin.service;

import org.csu.mypetstore.admin.domain.enums.OrderStatus;
import org.csu.mypetstore.admin.entity.SalesOrder;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.mapper.SalesOrderItemMapper;
import org.csu.mypetstore.admin.mapper.SalesOrderMapper;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAdminServiceTest {

    @Mock
    private SalesOrderMapper orderMapper;

    @Mock
    private SalesOrderItemMapper itemMapper;

    @Mock
    private StoreUserMapper userMapper;

    private OrderAdminService service;

    @BeforeEach
    void setUp() {
        service = new OrderAdminService(orderMapper, itemMapper, userMapper);
    }

    @Test
    void shipSucceedsForPaidOrder() {
        when(orderMapper.ship(16L)).thenReturn(1);

        assertThatCode(() -> service.ship(16L)).doesNotThrowAnyException();
    }

    @Test
    void repeatedShipKeepsFirstTimeAndReportsConflict() {
        when(orderMapper.ship(16L)).thenReturn(0);
        when(orderMapper.selectById(16L)).thenReturn(SalesOrder.builder()
                .id(16L)
                .status(OrderStatus.SHIPPED)
                .build());

        assertThatThrownBy(() -> service.ship(16L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("订单已发货，已保留首次发货时间。");
    }

    @Test
    void pendingOrderCannotShip() {
        when(orderMapper.ship(1L)).thenReturn(0);
        when(orderMapper.selectById(1L)).thenReturn(SalesOrder.builder()
                .id(1L)
                .status(OrderStatus.PENDING_PAYMENT)
                .build());

        assertThatThrownBy(() -> service.ship(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("当前订单状态不允许发货。");
    }

    @Test
    void missingOrderIsNotFound() {
        when(orderMapper.ship(999L)).thenReturn(0);
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.ship(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("订单不存在。");
    }

    @Test
    void invalidStatusFilterIsBadRequest() {
        assertThatThrownBy(() -> service.parseStatus("BOGUS"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("未知的订单状态。");
    }
}
