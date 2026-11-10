package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.mypetstore.entity.ProductImage;

import java.util.List;

public interface ProductImageMapper extends BaseMapper<ProductImage> {

    @Select("SELECT * FROM product_image WHERE product_id = #{productId} ORDER BY sort_order, id")
    List<ProductImage> selectByProductId(@Param("productId") Long productId);
}
