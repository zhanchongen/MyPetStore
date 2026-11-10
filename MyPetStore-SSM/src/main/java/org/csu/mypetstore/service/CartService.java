package org.csu.mypetstore.service;

import org.csu.mypetstore.dto.CartLine;
import org.csu.mypetstore.dto.CartView;
import org.csu.mypetstore.entity.CartItem;
import org.csu.mypetstore.entity.Product;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.CartItemMapper;
import org.csu.mypetstore.mapper.ProductMapper;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    public static final int MAX_KINDS = 20;
    public static final int MAX_QUANTITY = 99;

    private final StoreUserMapper userMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;

    public CartService(StoreUserMapper userMapper, ProductMapper productMapper, CartItemMapper cartItemMapper) {
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.cartItemMapper = cartItemMapper;
    }

    public CartView view(long userId) {
        List<CartLine> lines = cartItemMapper.selectLinesByUser(userId);
        BigDecimal validTotal = BigDecimal.ZERO;
        int totalQuantity = 0;
        boolean hasInvalid = false;
        for (CartLine line : lines) {
            totalQuantity += line.getQuantity() == null ? 0 : line.getQuantity();
            if (line.isAvailable()) {
                validTotal = validTotal.add(line.getLineAmount());
            } else {
                hasInvalid = true;
            }
        }
        boolean allAvailable = !lines.isEmpty() && !hasInvalid;
        return new CartView(lines, totalQuantity, validTotal, hasInvalid, allAvailable);
    }

    public int count(long userId) {
        return cartItemMapper.sumQuantityByUser(userId);
    }

    @Transactional
    public void add(long userId, long productId, int quantity) {
        lockUser(userId);
        requireQuantity(quantity);
        Product product = requireOnSaleProduct(productId);
        if (product.getStock() <= 0) {
            throw new BusinessException("商品已售罄，无法加入购物车。");
        }

        CartItem existing = cartItemMapper.selectByUserAndProductForUpdate(userId, productId);
        if (existing != null) {
            int newQuantity = existing.getQuantity() + quantity;
            if (newQuantity > MAX_QUANTITY) {
                throw new BusinessException("单个商品最多购买 99 件。");
            }
            if (newQuantity > product.getStock()) {
                throw new BusinessException("库存不足，当前仅剩 " + product.getStock() + " 件。");
            }
            int updated = cartItemMapper.updateQuantityWithVersion(
                    existing.getId(), userId, newQuantity, existing.getVersion());
            if (updated == 0) {
                throw new ConflictException("购物车已发生变化，请刷新后重试。");
            }
            return;
        }

        if (cartItemMapper.countByUser(userId) >= MAX_KINDS) {
            throw new BusinessException("购物车最多保存 20 种商品。");
        }
        if (quantity > product.getStock()) {
            throw new BusinessException("库存不足，当前仅剩 " + product.getStock() + " 件。");
        }
        cartItemMapper.insert(CartItem.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .version(0L)
                .build());
    }

    @Transactional
    public void update(long userId, long itemId, int quantity, long version) {
        lockUser(userId);
        requireQuantity(quantity);
        CartItem item = cartItemMapper.selectByIdAndUser(itemId, userId);
        if (item == null) {
            throw new NotFoundException("购物车条目不存在。");
        }
        Product product = requireOnSaleProduct(item.getProductId());
        if (product.getStock() <= 0) {
            throw new BusinessException("商品已售罄，无法修改数量。");
        }
        if (quantity > product.getStock()) {
            throw new BusinessException("库存不足，当前仅剩 " + product.getStock() + " 件。");
        }
        int updated = cartItemMapper.updateQuantityWithVersion(itemId, userId, quantity, version);
        if (updated == 0) {
            throw new ConflictException("购物车已发生变化，请刷新后重试。");
        }
    }

    @Transactional
    public void remove(long userId, long itemId, long version) {
        lockUser(userId);
        CartItem item = cartItemMapper.selectByIdAndUser(itemId, userId);
        if (item == null) {
            throw new NotFoundException("购物车条目不存在。");
        }
        int deleted = cartItemMapper.deleteByIdAndUser(itemId, userId, version);
        if (deleted == 0) {
            throw new ConflictException("购物车已发生变化，请刷新后重试。");
        }
    }

    private void lockUser(long userId) {
        if (userMapper.lockById(userId) == null) {
            throw new NotFoundException("用户不存在。");
        }
    }

    private void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException("商品数量必须为 1—99 的整数。");
        }
    }

    private Product requireOnSaleProduct(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !product.isOnSale()) {
            throw new BusinessException("商品不存在或已下架。");
        }
        return product;
    }
}
