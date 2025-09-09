package com.dazo66.crawler.kemono;

public class KemonoContents {


    /**
     * 数据修复sql:
     * UPDATE t_crawler_request
     * SET url = REPLACE(url, 'posts-legacy', 'posts')
     * WHERE url LIKE '%kemono%';
     */
    public static final String KEMONO_HOST = "https://kemono.cr/";

}
