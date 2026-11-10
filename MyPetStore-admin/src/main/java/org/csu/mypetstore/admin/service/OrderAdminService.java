package org.csu.mypetstore.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.admin.domain.enums.OrderStatus;
import org.csu.mypetstore.admin.dto.AdminOrderDetail;
import org.csu.mypetstore.admin.dto.AdminOrderRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.entity.SalesOrder;
import org.csu.mypetstore.admin.entity.SalesOrderItem;
import org.csu.mypetstore.admin.entity.StoreUser;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.mapper.SalesOrderItemMapper;
import org.csu.mypetstore.admin.mapper.SalesOrderMapper;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 全站订单查询与发货；管理端不能修改金额、明细或支付凭证。 */
@Service
public class OrderAdminService {

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper itemMapper;
    private final StoreUserMapper userMapper;

    public OrderAdminService(SalesOrderMapper orderMapper,
                             SalesOrderItemMapper itemMapper,
                             StoreUserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
    }

    public PageResult<AdminOrderRow> search(String orderNoRaw, String statusRaw, int page, int pageSize) {
        String orderNo = orderNoRaw == null ? null : orderNoRaw.trim();
        if (orderNo != null && orderNo.length() > 40) {
            throw new BadRequestException("订单号不能超过 40 个字符。");
        }
        OrderStatus status = parseStatus(statusRaw);
        QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq("order_no", orderNo);
        }
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        Page<SalesOrder> result = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<Long> userIds = new ArrayList<>();
        for (SalesOrder order : result.getRecords()) {
            if (order.getUserId() != null && !userIds.contains(order.getUserId())) {
                userIds.add(order.getUserId());
            }
        }
        Map<Long, String> usernames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (StoreUser user : userMapper.selectBatchIds(userIds)) {
                usernames.put(user.getId(), user.getUsername());
            }
        }
        List<AdminOrderRow> rows = new ArrayList<>();
        for (SalesOrder order : result.getRecords()) {
            rows.add(new AdminOrderRow(
                    order.getId(),
                    order.getOrderNo(),
                    usernames.getOrDefault(order.getUserId(), "—"),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getReceiverName(),
                    order.getCreatedAt()));
        }
        return new PageResult<>(rows, result.getTotal(), page, pageSize);
    }

    public AdminOrderDetail detail(Long id) {
        SalesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new NotFoundException("订单不存在。");
        }
        StoreUser user = order.getUserId() == null ? null : userMapper.selectById(order.getUserId());
        List<SalesOrderItem> items = itemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                .eq("order_id", id)
                .orderByAsc("id"));
        return new AdminOrderDetail(order, user, items);
    }

    /** 条件发货：只有 PAID 成功，重复发货保留首次 shipped_at。 */
    @Transactional
    public void ship(Long id) {
        if (orderMapper.ship(id) > 0) {
            return;
        }
        SalesOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new NotFoundException("订单不存在。");
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new ConflictException("订单已发货，已保留首次发货时间。");
        }
        throw new ConflictException("当前订单状态不允许发货。");
    }

    public OrderStatus parseStatus(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        OrderStatus status = OrderStatus.from(value);
        if (status == null) {
            throw new BadRequestException("未知的订单状态。");
        }
        return status;
    }
}
