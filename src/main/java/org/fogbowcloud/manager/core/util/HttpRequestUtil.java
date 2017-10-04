package org.fogbowcloud.manager.core.util;

import java.util.Properties;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;

public class HttpRequestUtil {
	
	private static final Logger LOGGER = Logger.getLogger(HttpRequestUtil.class);	
	protected static int DEFAULT_TIMEOUT_REQUEST = 60000; // 1 minute
	
	private static Integer timeoutHttpRequest;
	
	public static void init(Properties properties) {
		try {
			if (properties == null) {
				timeoutHttpRequest = DEFAULT_TIMEOUT_REQUEST;
			}
			
			String timeoutRequestStr = properties.getProperty(ConfigurationConstants.TIMEOUT_HTTP_REQUEST);
			timeoutHttpRequest = Integer.parseInt(timeoutRequestStr);
		} catch (NullPointerException|NumberFormatException e) {
			LOGGER.debug("Setting HttpRequestUtil timeout with default: " + DEFAULT_TIMEOUT_REQUEST + " ms.");
			timeoutHttpRequest = DEFAULT_TIMEOUT_REQUEST;
		} catch (Exception e) {
			LOGGER.error("Is not possible to initialize HttpRequestUtil.", e);
			throw e;
		}
		LOGGER.info("The HttpRequestUtil timeout is: " + timeoutHttpRequest + " ms.");
	}
	
	public static CloseableHttpClient createHttpClient() {
		return createHttpClient(null, null, null);
	}
	
	public static CloseableHttpClient createHttpClient(SSLConnectionSocketFactory sslsf) {
		return createHttpClient(null, sslsf, null);
	}
	
	public static CloseableHttpClient createHttpClient(HttpClientConnectionManager connManager) {
		return createHttpClient(null, null, connManager);
	}	
	
	public static CloseableHttpClient createHttpClient(Integer timeout, SSLConnectionSocketFactory sslsf, HttpClientConnectionManager connManager) {
		if (timeoutHttpRequest == null) {
			init(null); // Set to default timeout.
		}
		
		HttpClientBuilder builder = HttpClientBuilder.create();
		
		RequestConfig.Builder requestBuilder = RequestConfig.custom();
		requestBuilder = requestBuilder.setSocketTimeout(timeout != null ? timeout : timeoutHttpRequest);
		builder.setDefaultRequestConfig(requestBuilder.build());
		
		if (sslsf != null) {
			builder.setSSLSocketFactory(sslsf);
		}
		
		if (connManager != null) {
			builder.setConnectionManager(connManager);
		}
		
		return builder.build();
	}
	
	protected static int getTimeoutHttpRequest() {
		return timeoutHttpRequest;
	}
	
	protected static void setTimeoutHttpRequest(Integer timeoutHttpRequest) {
		HttpRequestUtil.timeoutHttpRequest = timeoutHttpRequest;
	}
	
}