UPDATE IGNORE t_crawler_request
SET url = REPLACE(
        REPLACE(url, 'https://kemono.cr/api/v1/', '/'),
        'https://kemono.su/api/v1/', '/'
          )
WHERE url LIKE 'https://kemono.cr/api/v1/%'
   OR url LIKE 'https://kemono.su/api/v1/%';

DELETE
old_url
FROM t_crawler_request old_url
JOIN t_crawler_request relative_url
  ON relative_url.url = REPLACE(
          REPLACE(old_url.url, 'https://kemono.cr/api/v1/', '/'),
          'https://kemono.su/api/v1/', '/'
                        )
WHERE old_url.url LIKE 'https://kemono.cr/api/v1/%'
   OR old_url.url LIKE 'https://kemono.su/api/v1/%';


SELECT trx_id,
       trx_state,
       trx_started,
       trx_mysql_thread_id,
       trx_rows_locked,
       trx_rows_modified,
       trx_query
FROM information_schema.innodb_trx
ORDER BY trx_started;

