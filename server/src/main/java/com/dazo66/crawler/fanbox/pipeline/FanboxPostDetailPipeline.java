package com.dazo66.crawler.fanbox.pipeline;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.config.CookieConfig;
import com.dazo66.crawler.fanbox.FanboxPostDetail;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.DownloadAction;
import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.dazo66.crawler.kemono.pipeline.KemonoImagePipeline.getNumberLen;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/9 1:56
 **/
@Slf4j
@Service
public class FanboxPostDetailPipeline implements Pipeline<FanboxPostDetail> {

    @Resource(name = "downloadThreadPool")
    private ThreadPoolExecutor executors;
    @Value("${fanbox.save.path}")
    private String fileSavePath;
    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private CookieConfig cookieConfig;

    private Map<String, String> fanboxHeaders = new HashMap<String, String>() {
        {
            put("Referer", "https://www.fanbox.cc/");
            put("Origin", "https://www.fanbox.cc");
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        }
    };

    @Override
    public void process(FanboxPostDetail bean) {
        if (bean.getIsRestricted()) {
            log.error("这个post没权限：{}", JSONObject.toJSONString(bean));
            return;
        }
        Artist artist = kemonoArtistService.getArtistId(bean.getArtistId(), ArtistDomainEnum.FANBOX);
        if (artist == null) {
            log.error("找不到这个用户：{}", bean.getArtistId());
            return;
        }
        List<? extends FanboxPostDetail.DownloadObject> downloadObject = bean.getDownloadObject();
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(bean.getImageForShare());
        imageUrls.addAll(downloadObject.stream().map(FanboxPostDetail.DownloadObject::getUrl).collect(Collectors.toList()));
        List<Future<Boolean>> list = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            DownloadAction task = new DownloadAction(imageUrls.get(i), getDownloadPath(bean, artist, imageUrls.size(), i), artist.getCompression());
            task.setHeaders(fanboxHeaders);
            task.setCookie(cookieConfig.getFanboxCookie());
            Future<Boolean> future = executors.submit(task);
            list.add(future);
        }
        boolean isDone = true;
        for (Future<Boolean> future : list) {
            try {
                if (!future.get()) {
                    isDone = false;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("等待图片下载中出错", e);
            }
        }
        if (isDone) {
            crawlerRequestService.updateByUrl(new CrawlerRequest().setUrl(bean.getRequest().getUrl()).setIsDone(true));
            kemonoArtistService.updateByArtistId(new Artist().setArtistId(bean.getArtistId()).setLastUpdate(new Date()));
        } else {
            CrawlerRequest crawlerRequest = crawlerRequestService.getByUrl(bean.getRequest().getUrl());
            crawlerRequestService.updateByUrl(new CrawlerRequest().setUrl(bean.getRequest().getUrl()).setErrorCount(crawlerRequest.getErrorCount() == null ? 1 : crawlerRequest.getErrorCount() + 1));
        }
    }

    public String getDownloadPath(FanboxPostDetail post, Artist artist, int total, int index) {
        String time = post.getPublishedDatetime();
        if (StringUtils.isEmpty(time) && time.length() < 10) {
            time = DateUtils.format(new Date(), "yyyy-MM-dd");
        } else {
            time = time.substring(0, 10);
        }
        return fileSavePath + String.format("/%s/%s-%s-%0" + getNumberLen(total) + "d", StringUtil.cleanSym(artist.getName()), time, StringUtil.cleanSym(post.getTitle()), index);
    }

}
