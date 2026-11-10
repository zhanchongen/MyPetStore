package org.csu.mypetstore.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.csu.mypetstore.admin.entity.StoreUser;

@Mapper
public interface StoreUserMapper extends BaseMapper<StoreUser> {

    @Select("SELECT * FROM store_user WHERE username = #{username} LIMIT 1")
    StoreUser selectByUsername(@Param("username") String username);

    @Update("UPDATE store_user SET status = #{status}, updated_at = NOW() "
            + "WHERE id = #{id} AND role = 'CUSTOMER'")
    int updateCustomerStatus(@Param("id") Long id, @Param("status") String status);
}
