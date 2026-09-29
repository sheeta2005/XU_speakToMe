package com.xiyou.speakToMe.framework.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.util.List;

/**
 * 统一分页响应：{ list, total, page, size }
 */
@Data
public class PageResult<T> {

    private List<T> list;

    private long total;

    private long page;

    private long size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }

    /** 由 MyBatis-Plus Page 直接转换 */
    public static <T> PageResult<T> of(Page<T> page) {
        return of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
