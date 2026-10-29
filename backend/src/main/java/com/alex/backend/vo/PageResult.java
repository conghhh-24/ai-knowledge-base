package com.alex.backend.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private int page;
    private int pageSize;
    private List<T> items;

    public PageResult(long total, int page, int pageSize, List<T> items) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.items = items;
    }
}
