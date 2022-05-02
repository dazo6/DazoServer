package com.dazo66.service;

import com.dazo66.crawler.FanboxPost;
import com.dazo66.util.DownloadAction;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * @author Dazo66
 */
@Service
public class FanboxImagePipeline implements Pipeline<FanboxPost> {


	private final ThreadPoolExecutor executors = new ThreadPoolExecutor(3, 3, 0L,
			TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
			new ThreadFactoryBuilder().setNameFormat("download-%02d").build());
	private final CloseableHttpClient httpClient;
	@Value("${fanbox.save.path}")
	private String fileSavePath;

	{
		RequestConfig clientConfig =
				RequestConfig.custom().setConnectTimeout(600000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();
		PoolingHttpClientConnectionManager syncConnectionManager =
				new PoolingHttpClientConnectionManager();
		syncConnectionManager.setMaxTotal(1000);
		syncConnectionManager.setDefaultMaxPerRoute(50);
		httpClient =
				HttpClientBuilder.create().setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();
	}

	@Override
	public void process(FanboxPost bean) {
		Object[] images = bean.getImages();
		for (int i = 0; i < images.length; i++) {
			Object url = images[i];
			executors.execute(new DownloadAction(httpClient, url.toString(), getDownloadPath(bean,
					i)));
		}
	}


	public String getDownloadPath(FanboxPost post, int index) {
		return fileSavePath + String.format("/%s/%s-%s-%0" + getNumberLen(post.getImages().length) + "d" + ".jpg", post.getArtistName(), post.getTime().substring(0, 10), post.getTitle(), index);
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
