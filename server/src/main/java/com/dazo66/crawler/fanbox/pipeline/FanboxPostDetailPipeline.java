package com.dazo66.crawler.fanbox.pipeline;

import com.dazo66.crawler.fanbox.FanboxPostDetail;
import com.geccocrawler.gecco.pipeline.Pipeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.ListUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/9 1:56
 **/
@Service
public class FanboxPostDetailPipeline implements Pipeline<FanboxPostDetail> {


    private static Map<String, String> fanboxHeaders = new HashMap<String, String>() {
        {
            put("Referer", "https://www.fanbox.cc/");
            put("Origin", "https://www.fanbox.cc");
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        }
    };

    @Value("${fanbox.save.cookie")
    private String fanboxsessid;
    private Map<String, String> cookies = new HashMap<>();

    @PostConstruct
    public void init() {
        cookies.put("FANBOXSESSID", fanboxsessid);
    }


    @Override
    public void process(FanboxPostDetail bean) {
        if (!ListUtils.isEmpty(bean.getBody().getImages())) {

        }

    }
}
