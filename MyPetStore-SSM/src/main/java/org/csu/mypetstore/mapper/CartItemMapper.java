package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.csu.mypetstore.dto.CartLine;
import org.csu.mypetstore.entity.CartItem;

import java.util.List;

public interface CartItemMapper extends BaseMapper<CartItem> {

    List<CartLine> selectLinesByUser(@Param("userId") Long userId);

    CartItem selectByUserAndProductForUpdate(@Param("userId") Long userId,
                                             @Param("productId") Long productId);

    CartItem selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    List<CartItem> selectByUserForUpdate(@Param("userId") Long userId);

    int sumQuantityByUser(@Param("userId") Long userId);

    int countByUser(@Param("userId") Long userId);

    int updateQuantityWithVersion(@Param("id") Long id,
                                  @Param("userId") Long userId,
                                  @Param("quantity") int quantity,
                                  @Param("version") long version);

    int deleteByIdAndUser(@Param("id") Long id,
                          @Param("userId") Long userId,
                          @Param("version") long version);

    int deleteByIdsAndUser(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
