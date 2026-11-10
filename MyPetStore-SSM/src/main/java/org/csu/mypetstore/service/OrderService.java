package org.csu.mypetstore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.common.OrderNumberGenerator;
import org.csu.mypetstore.domain.enums.OrderStatus;
import org.csu.mypetstore.dto.CartLine;
import org.csu.mypetstore.dto.CheckoutConfirm;
import org.csu.mypetstore.dto.CheckoutForm;
import org.csu.mypetstore.dto.OrderDetail;
import org.csu.mypetstore.dto.OrderRow;
import org.csu.mypetstore.dto.PageResult;
import org.csu.mypetstore.entity.CartItem;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.entity.SalesOrder;
import org.csu.mypetstore.entity.SalesOrderItem;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.CartItemMapper;
import org.csu.mypetstore.mapper.ProductMapper;
import org.csu.mypetstore.mapper.SalesOrderItemMapper;
import org.csu.mypetstore.mapper.SalesOrderMapper;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final int CONFIRM_MINUTES = 30;

    private final StoreUserMapper userMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final OrderNumberGenerator numberGenerator;

    public OrderService(StoreUserMapper userMapper,
                        ProductMapper productMapper,
                        CartItemMapper cartItemMapper,
                        SalesOrderMapper orderMapper,
                        SalesOrderItemMapper orderItemMapper,
                        OrderNumberGenerator numberGenerator) {
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.cartItemMapper = cartItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.numberGenerator = numberGenerator;
    }

    @Transactional
    public long placeOrder(long userId, CheckoutConfirm confirm, CheckoutForm form) {
        if (userMapper.lockById(userId) == null) {
            throw new NotFoundException("用户不存在。");
        }
        requireConfirm(confirm, userId);
        requireReceiver(form);

        List<CartItem> lockedCart = cartItemMapper.selectByUserForUpdate(userId);
        List<CartLine> lines = cartItemMapper.selectLinesByUser(userId);
        if (lockedCart.isEmpty() || lines.isEmpty()) {
            throw new BusinessException("购物车为空，请重新选择商品。");
        }
        if (lockedCart.size() != lines.size()) {
            throw new ConflictException("购物车已发生变化，请重新确认。");
        }
        compareConfirm(confirm, lines);

        for (CartLine line : lines) {
            if (!line.isAvailable()) {
                throw new BusinessException("商品「" + line.getName() + "」不可结算：" + line.invalidReason());
            }
        }

        List<Long> productIds = lines.stream()
                .map(CartLine::getProductId)
                .sorted()
                .toList();
        List<Product> lockedProducts = productMapper.selectForUpdateByIds(productIds);
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : lockedProducts) {
            productMap.put(product.getId(), product);
        }
        if (productMap.size() != productIds.size()) {
            throw new BusinessException("部分商品已不存在，请刷新购物车。");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<SalesOrderItem> items = new ArrayList<>();
        for (CartLine line : lines) {
            Product product = productMap.get(line.getProductId());
            if (product == null || !product.isOnSale()) {
                throw new BusinessException("商品「" + line.getName() + "」已下架。");
            }
            if (product.getPrice() == null || product.getPrice().compareTo(line.getPrice()) != 0) {
                throw new ConflictException("商品价格已发生变化，请重新确认。");
            }
            if (product.getStock() == null || product.getStock() < line.getQuantity()) {
                throw new BusinessException("库存不足，当前仅剩 "
                        + (product.getStock() == null ? 0 : product.getStock()) + " 件。");
            }

            BigDecimal lineAmount = product.getPrice()
                    .multiply(BigDecimal.valueOf(line.getQuantity()))
                    .setScale(2, RoundingMode.UNNECESSARY);
            total = total.add(lineAmount);
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0 || total.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException("订单金额超出允许范围。");
        }
        if (confirm.totalAmount() == null || confirm.totalAmount().compareTo(total) != 0) {
            throw new ConflictException("订单金额已发生变化，请重新确认。");
        }

        LocalDateTime now = LocalDateTime.now();
        SalesOrder order = SalesOrder.builder()
                .orderNo(numberGenerator.orderNo())
                .userId(userId)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(total)
                .receiverName(form.getReceiverName().trim())
                .receiverPhone(form.getReceiverPhone().trim())
                .receiverAddress(form.getReceiverAddress().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();
        orderMapper.insert(order);

        for (CartLine line : lines) {
            Product product = productMap.get(line.getProductId());
            BigDecimal lineAmount = product.getPrice()
                    .multiply(BigDecimal.valueOf(line.getQuantity()))
                    .setScale(2, RoundingMode.UNNECESSARY);
            items.add(SalesOrderItem.builder()
                    .orderId(order.getId())
                    .productId(product.getId())
                    .skuSnapshot(product.getSku())
                    .productNameSnapshot(product.getName())
                    .coverImageSnapshot(product.getCoverImage())
                    .unitPrice(product.getPrice())
                    .quantity(line.getQuantity())
                    .lineAmount(lineAmount)
                    .build());
        }
        orderItemMapper.insertBatch(items);

        for (CartLine line : lines) {
            int updated = productMapper.decreaseStock(line.getProductId(), line.getQuantity());
            if (updated == 0) {
                throw new BusinessException("库存不足或商品已下架，订单未创建。");
            }
        }

        List<Long> itemIds = lines.stream().map(CartLine::getItemId).toList();
        int deleted = cartItemMapper.deleteByIdsAndUser(userId, itemIds);
        if (deleted != lines.size()) {
            throw new ConflictException("购物车已发生变化，请重新确认。");
        }
        return order.getId();
    }

    public PageResult<OrderRow> listUser(long userId,
                                         OrderStatus status,
                                         String orderNo,
                                         int page,
                                         int pageSize) {
        String normalizedOrderNo = orderNo == null ? null : orderNo.trim();
        if (normalizedOrderNo != null && normalizedOrderNo.length() > 40) {
            throw new BadRequestException("订单号最多 40 个字符。");
        }
        if (normalizedOrderNo != null && normalizedOrderNo.isEmpty()) {
            normalizedOrderNo = null;
        }
        Page<OrderRow> queryPage = new Page<>(page, pageSize);
        var result = orderMapper.selectUserPage(queryPage, userId, status, normalizedOrderNo);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public OrderDetail detailForUser(long userId, long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new NotFoundException("订单不存在。");
        }
        return new OrderDetail(order, orderItemMapper.selectByOrderId(orderId));
    }

    @Transactional
    public void cancel(long userId, long orderId) {
        SalesOrder order = lockOwnedOrder(userId, orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，请勿重复操作。");
        }
        if (!order.getStatus().canCancel()) {
            throw new BusinessException("当前状态不能取消订单。");
        }

        List<SalesOrderItem> items = new ArrayList<>(orderItemMapper.selectByOrderId(orderId));
        items.sort(Comparator.comparing(SalesOrderItem::getProductId));
        for (SalesOrderItem item : items) {
            int updated = productMapper.increaseStock(item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException("商品不存在，无法恢复库存，订单未取消。");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.updateToCancelled(orderId, now, now);
        if (updated == 0) {
            throw new ConflictException("订单状态已发生变化，请刷新后重试。");
        }
    }

    public enum PaymentResult {
        SUCCESS, ALREADY_PAID
    }

    @Transactional
    public PaymentResult pay(long userId, long orderId) {
        SalesOrder order = lockOwnedOrder(userId, orderId);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            LocalDateTime now = LocalDateTime.now();
            int updated = orderMapper.updateToPaid(orderId, numberGenerator.paymentNo(), now, now);
            if (updated == 0) {
                throw new ConflictException("订单状态已发生变化，请刷新后重试。");
            }
            return PaymentResult.SUCCESS;
        }
        if (order.getStatus().isPaid() || order.getStatus().isShipped() || order.getStatus().isCompleted()) {
            return PaymentResult.ALREADY_PAID;
        }
        throw new BusinessException("当前状态不能支付。");
    }

    public void failPayment(long userId, long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new NotFoundException("订单不存在。");
        }
        if (!order.getStatus().canPay()) {
            throw new BusinessException("当前状态不能发起支付。");
        }
    }

    @Transactional
    public void complete(long userId, long orderId) {
        SalesOrder order = lockOwnedOrder(userId, orderId);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException("订单已完成，请勿重复操作。");
        }
        if (!order.getStatus().canComplete()) {
            throw new BusinessException("只有已发货订单可以确认收货。");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.updateToCompleted(orderId, now, now);
        if (updated == 0) {
            throw new ConflictException("订单状态已发生变化，请刷新后重试。");
        }
    }

    private SalesOrder lockOwnedOrder(long userId, long orderId) {
        SalesOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new NotFoundException("订单不存在。");
        }
        return order;
    }

    private void requireConfirm(CheckoutConfirm confirm, long userId) {
        if (confirm == null || confirm.createdAt() == null || confirm.lines() == null) {
            throw new BusinessException("结算确认已失效，请重新确认。");
        }
        if (!confirm.userId().equals(userId)) {
            throw new BusinessException("结算确认不属于当前用户。");
        }
        if (confirm.createdAt().isBefore(LocalDateTime.now().minusMinutes(CONFIRM_MINUTES))) {
            throw new BusinessException("结算确认已过期，请重新确认。");
        }
    }

    private void compareConfirm(CheckoutConfirm confirm, List<CartLine> lines) {
        if (confirm.lines().size() != lines.size()) {
            throw new ConflictException("购物车已发生变化，请重新确认。");
        }
        Map<Long, CheckoutConfirm.CheckoutLine> confirmed = new HashMap<>();
        for (CheckoutConfirm.CheckoutLine line : confirm.lines()) {
            confirmed.put(line.itemId(), line);
        }
        for (CartLine line : lines) {
            CheckoutConfirm.CheckoutLine expected = confirmed.get(line.getItemId());
            if (expected == null
                    || !expected.productId().equals(line.getProductId())
                    || !expected.quantity().equals(line.getQuantity())
                    || !expected.version().equals(line.getVersion())
                    || expected.unitPrice().compareTo(line.getPrice()) != 0) {
                throw new ConflictException("购物车或价格已发生变化，请重新确认。");
            }
        }
    }

    private void requireReceiver(CheckoutForm form) {
        if (form == null) {
            throw new BadRequestException("收货信息不能为空。");
        }
        if (form.getReceiverName() == null || form.getReceiverName().trim().isEmpty()
                || form.getReceiverName().trim().length() > 50) {
            throw new BadRequestException("收货人必须为 1—50 个字符。");
        }
        if (form.getReceiverPhone() == null || form.getReceiverPhone().trim().isEmpty()
                || form.getReceiverPhone().trim().length() > 30) {
            throw new BadRequestException("联系电话必须为 1—30 个字符。");
        }
        if (form.getReceiverAddress() == null || form.getReceiverAddress().trim().isEmpty()
                || form.getReceiverAddress().trim().length() > 255) {
            throw new BadRequestException("详细地址必须为 1—255 个字符。");
        }
    }
}
