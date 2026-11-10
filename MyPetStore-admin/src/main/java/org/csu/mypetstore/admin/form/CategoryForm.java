package org.csu.mypetstore.admin.form;

import lombok.Data;

/** 分类新增/编辑表单。编辑时 code 与 parentId 只用于展示，服务端忽略。 */
@Data
public class CategoryForm {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private Integer sortOrder;
}
