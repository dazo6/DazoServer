package com.dazo66.service;

import com.dazo66.crawler.FanboxPost;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.entity.FanboxArtist;
import com.dazo66.util.DownloadAction;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.tools.ant.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;


/**
 * @author Dazo66
 */
@Service
@Slf4j
public class FanboxImagePipeline implements Pipeline<FanboxPost> {


    private final ThreadPoolExecutor executors = new ThreadPoolExecutor(3, 3, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("download-%02d").build());
    @Autowired
    private CloseableHttpClient fanboxHttpClient;
    @Value("${fanbox.save.path}")
    private String fileSavePath;

    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private FanboxArtistService fanboxArtistService;

    @Override
    public void process(FanboxPost bean) {
        try {
            Object[] images = bean.getImages();
            List<Future<Boolean>> list = new ArrayList<>();
            for (int i = 0; i < images.length; i++) {
                Object url = images[i];
                Future<Boolean> future = executors.submit(new DownloadAction(fanboxHttpClient,
                        url.toString(), getDownloadPath(bean, i)));
                list.add(future);
            }
            if (bean.getDownloads().length == bean.getDownloadsName().length) {
                for (int i = 0; i < bean.getDownloads().length; i++) {
                    Object url = bean.getDownloads()[i];
                    String name = bean.getDownloadsName()[i].toString();
                    if (name.lastIndexOf('.') != -1) {
                        name = name.substring(0, name.lastIndexOf('.'));
                    }
                    if (name.startsWith("Download ")) {
                        name = name.substring(9);
                    }
                    Future<Boolean> future = executors.submit(new DownloadAction(fanboxHttpClient
                            , url.toString(), getDownloadPath(bean, name)));
                    list.add(future);
                }
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
            }
        } catch (Exception e) {
            log.error("下载post中的图片出错: ", e);
        }

    }


    public String getDownloadPath(FanboxPost post, int index) {
        FanboxArtist artist = fanboxArtistService.getArtistId(post.getArtistId());
        String time = post.getTime();
        if (StringUtils.isEmpty(time)) {
            time = DateUtils.format(new Date(), "yyyy-MM-dd");
        }
        return fileSavePath + String.format("/%s/%s-%s-%0" + getNumberLen(post.getImages().length) + "d", cleanSym(artist.getName()), time.substring(0, 10), cleanSym(post.getTitle()), index);
    }

    public String getDownloadPath(FanboxPost post, String name) {
        FanboxArtist artist = fanboxArtistService.getArtistId(post.getArtistId());
        return fileSavePath + String.format("/%s/%s-%s-%s", cleanSym(artist.getName()),
                post.getTime().substring(0, 10), cleanSym(post.getTitle()), cleanSym(name));
    }

    private static String cleanSym(String s) {
        String replace =
                s.replace("/", "").replace(":", "").replace("*", "").replace("\\", "").replace(
                        "|", "").replace("<", "").replace(">", "").replace("?", "").replace("\"",
                        "");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < replace.length(); i++) {
            char c = replace.charAt(i);
            if (c >= 'A' || (c >= '0' && c <= '9') || c == '-') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static int getNumberLen(int num) {
        int count = 0;
        while (num != 0) {
            num /= 10;
            ++count;
        }
        return count;
    }
}
