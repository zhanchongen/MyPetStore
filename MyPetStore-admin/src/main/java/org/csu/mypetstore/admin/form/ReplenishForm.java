package org.csu.mypetstore.admin.form;

import lombok.Data;

/** 补货表单：只提交商品标识与正整数增量，绝不提交库存绝对值。 */
@Data
public class ReplenishForm {

    private Long id;
    private String productName;
    private Integer quantity;
}
