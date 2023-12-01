package com.dazo66.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dazo66.mapper.BaseMapper;

import java.util.Iterator;
import java.util.List;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/6/3 18:24
 **/
public class BaseRepository<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {

    public Iterator<T> iterator(Wrapper<T> queryWrapper) {
        return new Iterator<T>() {

            int current = 0;
            int page = 1;
            int pageSize = 10;
            List<T> cache = null;

            @Override
            public boolean hasNext() {
                if (cache == null) {
                    loadList(page++, queryWrapper);
                }
                int index = getIndex();
                if (cache.size() > index) {
                    return true;
                } else if (cache.size() == pageSize) {
                    loadList(page++, queryWrapper);
                    return cache.size() > 0;
                } else {
                    return false;
                }
            }


            private void loadList(int page, Wrapper<T> queryWrapper) {
                cache = page(new Page<>(page, pageSize), queryWrapper).getRecords();
            }

            @Override
            public T next() {
                int index = getIndex();
                if (cache.size() > index) {
                    T ret = cache.get(index);
                    current++;
                    return ret;
                } else {
                    throw new IllegalStateException("");
                }
            }

            private int getIndex() {
                return current - ((page - 1) * pageSize);
            }
        };
    }


}
