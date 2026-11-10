package org.csu.mypetstore.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.mypetstore.admin.entity.Category;

/** 分类列表行：含父分类名称与引用计数，供删除前提示。 */
@Data
@AllArgsConstructor
public class CategoryRow {

    private Category category;
    private String parentName;
    private long childCount;
    private long productCount;

    public boolean isTopLevel() {
        return category.getParentId() == null;
    }
}
