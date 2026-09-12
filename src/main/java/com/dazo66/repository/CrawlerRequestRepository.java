package com.dazo66.repository;

import com.dazo66.entity.CrawlerRequest;
import com.dazo66.mapper.CrawlerRequestMapper;
import org.springframework.stereotype.Service;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/6/3 21:48
 **/
@Service
public class CrawlerRequestRepository extends BaseRepository<CrawlerRequestMapper, CrawlerRequest> {
}
