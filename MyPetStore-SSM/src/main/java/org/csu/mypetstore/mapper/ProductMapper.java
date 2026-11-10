package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.csu.mypetstore.domain.enums.ProductStatus;
import org.csu.mypetstore.dto.ProductCard;
import org.csu.mypetstore.dto.ProductQuery;
import org.csu.mypetstore.entity.Product;

import java.util.List;

public interface ProductMapper extends BaseMapper<Product> {

    IPage<ProductCard> selectUserPage(IPage<ProductCard> page, @Param("q") ProductQuery q);

    List<Product> selectHomeNew(@Param("limit") int limit);

    List<Product> selectHomeFeatured(@Param("limit") int limit);

    List<Product> selectForUpdateByIds(@Param("ids") List<Long> ids);

    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    int updateInfoWithVersion(@Param("p") Product product);

    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("status") ProductStatus status,
                                @Param("version") long version);
}
