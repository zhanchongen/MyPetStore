package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.csu.mypetstore.entity.StoreUser;

public interface StoreUserMapper extends BaseMapper<StoreUser> {

    @Select("SELECT * FROM store_user WHERE username = #{username}")
    StoreUser selectByUsername(@Param("username") String username);

    @Select("SELECT id FROM store_user WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    @Update("UPDATE store_user SET display_name = #{displayName}, email = #{email}, phone = #{phone} WHERE id = #{id}")
    int updateProfile(@Param("id") Long id,
                      @Param("displayName") String displayName,
                      @Param("email") String email,
                      @Param("phone") String phone);

    @Update("UPDATE store_user SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
