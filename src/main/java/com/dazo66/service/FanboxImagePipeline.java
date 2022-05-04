package com.dazo66.service;

import com.dazo66.crawler.FanboxPost;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.util.DownloadAction;
import com.dazo66.util.IpUtils;
import com.dazo66.util.LocalLock;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


/**
 * @author Dazo66
 */
@Service
@Slf4j
public class FanboxImagePipeline implements Pipeline<FanboxPost> {


	private final ThreadPoolExecutor executors = new ThreadPoolExecutor(10, 10, 0L,
			TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
			new ThreadFactoryBuilder().setNameFormat("download-%02d").build());
	private CloseableHttpClient httpClient;
	@Value("${fanbox.save.path}")
	private String fileSavePath;
	@Value("${fanbox.proxy.ip}")
	private String proxyIp;
	@Value("${fanbox.proxy.port}")
	private Integer proxyPort;
	@Autowired
	private CrawlerRequestService crawlerRequestService;

	@PostConstruct
	private void init() {
		RequestConfig clientConfig =
				RequestConfig.custom().setConnectTimeout(60000).setConnectionRequestTimeout(6000).setSocketTimeout(6000).build();
		PoolingHttpClientConnectionManager syncConnectionManager =
				new PoolingHttpClientConnectionManager();
		syncConnectionManager.setMaxTotal(1000);
		syncConnectionManager.setDefaultMaxPerRoute(50);
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if (IpUtils.checkIpPort(proxyIp, proxyPort)) {
			log.info("代理可用，开始使用代理通道");
			httpClientBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
		}
		httpClient =
				httpClientBuilder.setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();
	}

	@Override
	@LocalLock(lockBeanName = "ioLockObject")
	public void process(FanboxPost bean) {
		Object[] images = bean.getImages();
		List<Future<Boolean>> list = new ArrayList<>();
		for (int i = 0; i < images.length; i++) {
			Object url = images[i];
			Future<Boolean> future = executors.submit(new DownloadAction(httpClient,
					url.toString(), getDownloadPath(bean, i)));
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
		}
	}


	public String getDownloadPath(FanboxPost post, int index) {
		return fileSavePath + String.format("/%s/%s-%s-%0" + getNumberLen(post.getImages().length) + "d", cleanSym(post.getArtistName()), post.getTime().substring(0, 10), cleanSym(post.getTitle()), index);
	}

	private static String cleanSym(String s) {
		String replace =
				s.replace("/", "").replace(":", "").replace("*", "").replace("\\", "").replace("|"
						, "").replace("<", "").replace(">", "").replace("?", "").replace("\"", "");
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < replace.length(); i++) {
			char c = replace.charAt(i);
			if (c >= 'A') {
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
