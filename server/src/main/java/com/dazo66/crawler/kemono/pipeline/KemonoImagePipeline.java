package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.crawler.kemono.KemonoPost;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.DownloadAction;
import com.dazo66.util.KemonoHelper;
import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.tools.ant.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Dazo66
 */
@Service
@Slf4j
public class KemonoImagePipeline implements Pipeline<KemonoPost> {


    @Resource(name = "downloadThreadPool")
    private ThreadPoolExecutor executors;
    @Autowired
    private CloseableHttpClient fanboxHttpClient;
    @Value("${fanbox.save.path}")
    private String fileSavePath;

    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private KemonoArtistService kemonoArtistService;


    @Value("${kemono.username}")
    private String kemonoUsername;

    @Value("${kemono.password}")
    private String kemonoPassword;

    private static Map<String, String> kemonoHeaders = new HashMap<String, String>() {
        {
            put("referer", "https://kemono.su/");
            put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36");
        }
    };

    @Override
    @Transactional
    public void process(KemonoPost bean) {
        try {
            Artist artist = kemonoArtistService.getArtistId(bean.getArtistId(), ArtistDomainEnum.KEMONO);
            Object[] images = bean.getImages();
            List<Future<Boolean>> list = new ArrayList<>();
            for (int i = 0; i < images.length; i++) {
                Object url = images[i];
                DownloadAction task = new DownloadAction(fanboxHttpClient,
                        url.toString(), getDownloadPath(bean, i), artist.getCompression());
                task.setHeaders(kemonoHeaders);
                task.setUseMultiDownloader(false);
                task.setCookie(KemonoHelper.loginAndGetCookie(kemonoUsername, kemonoPassword));
                Future<Boolean> future = executors.submit(task);
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
                    DownloadAction task = new DownloadAction(fanboxHttpClient
                            , url.toString(), getDownloadPath(bean, name), artist.getCompression());
                    task.setHeaders(kemonoHeaders);
                    task.setUseMultiDownloader(false);
                    task.setCookie(KemonoHelper.loginAndGetCookie(kemonoUsername, kemonoPassword));
                    Future<Boolean> future = executors.submit(task);
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
                kemonoArtistService.updateByArtistId(new Artist().setArtistId(bean.getArtistId()).setLastUpdate(new Date()));
            }
        } catch (Exception e) {
            log.error("下载post中的图片出错: ", e);
        }

    }


    public String getDownloadPath(KemonoPost post, int index) {
        Artist artist = kemonoArtistService.getArtistId(post.getArtistId(), ArtistDomainEnum.KEMONO);
        String time = post.getTime();
        time = getDataStr(time);
        if (StringUtils.isEmpty(time)) {
            time = DateUtils.format(new Date(), "yyyy-MM-dd");
        }
        return fileSavePath + String.format("/%s/%s-%s-%0" + getNumberLen(post.getImages().length) + "d", StringUtil.cleanSym(artist.getName()), time, StringUtil.cleanSym(post.getTitle()), index);
    }

    public String getDownloadPath(KemonoPost post, String name) {

        Artist artist = kemonoArtistService.getArtistId(post.getArtistId(), ArtistDomainEnum.KEMONO);
        String time = post.getTime();
        time = getDataStr(time);
        if (StringUtils.isEmpty(time)) {
            time = DateUtils.format(new Date(), "yyyy-MM-dd");
        }
        return fileSavePath + String.format("/%s/%s-%s-%s", StringUtil.cleanSym(artist.getName()), time,
                StringUtil.cleanSym(post.getTitle()), StringUtil.cleanSym(name));
    }

    private static Pattern DATA_REGEX = Pattern.compile("(20\\d\\d-[0-1]\\d-[0-3]\\d)");

    public static String getDataStr(String string) {
        if (StringUtils.isEmpty(string)) {
            return "";
        }
        Matcher matcher = DATA_REGEX.matcher(string);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }


    public static int getNumberLen(int num) {
        int count = 0;
        while (num != 0) {
            num /= 10;
            ++count;
        }
        return count == 0 ? 1 : count;
    }
}
