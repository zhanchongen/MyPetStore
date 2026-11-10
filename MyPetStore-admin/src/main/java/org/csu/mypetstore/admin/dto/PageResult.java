package org.csu.mypetstore.admin.dto;

import java.util.List;

/** 统一分页结果，模板与 Controller 共用。 */
public record PageResult<T>(List<T> records, long total, long page, long pageSize) {

    public long pages() {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < pages();
    }

    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }

    public long fromIndex() {
        return total == 0 ? 0 : (page - 1) * pageSize + 1;
    }

    public long toIndex() {
        return Math.min(page * pageSize, total);
    }
}
