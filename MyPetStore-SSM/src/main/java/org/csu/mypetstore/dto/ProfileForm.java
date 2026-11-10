package org.csu.mypetstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 个人资料表单（P1）。 */
@Data
public class ProfileForm {

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 50, message = "显示名称最多 50 个字符")
    private String displayName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最多 100 个字符")
    private String email;

    @Size(max = 30, message = "电话最多 30 个字符")
    private String phone;
}
