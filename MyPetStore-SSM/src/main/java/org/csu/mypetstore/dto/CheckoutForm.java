package org.csu.mypetstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 结算收货信息，长度按 PRD 6.10。 */
@Data
public class CheckoutForm {

    @NotBlank(message = "收货人不能为空")
    @Size(max = 50, message = "收货人最多 50 个字符")
    private String receiverName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 30, message = "联系电话最多 30 个字符")
    private String receiverPhone;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址最多 255 个字符")
    private String receiverAddress;
}
