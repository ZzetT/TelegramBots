package org.telegram.telegrambots.bots;

import java.util.List;

import org.telegram.telegrambots.meta.ApiConstants;
import org.telegram.telegrambots.meta.generics.BotOptions;
import org.telegram.telegrambots.updatesreceivers.ExponentialBackOff;

/**
 * @author Ruben Bermudez
 * @version 1.0
 * @brief Configurations for the Bot
 * @date 21 of July of 2016
 */
public class DefaultBotOptions implements BotOptions {
	private int maxThreads; /// < Max number of threads used for async methods executions (default 1)
	private ExponentialBackOff exponentialBackOff;
	private Integer maxWebhookConnections;
	private String baseUrl;
	private List<String> allowedUpdates;
	private ProxyType proxyType;
	private String proxyHost;
	private int proxyPort;

	public enum ProxyType {
		NO_PROXY, HTTP, SOCKS4, SOCKS5
	}

	public DefaultBotOptions() {
		maxThreads = 1;
		baseUrl = ApiConstants.BASE_URL;
		proxyType = ProxyType.NO_PROXY;
	}

	@Override
	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	public Integer getMaxWebhookConnections() {
		return maxWebhookConnections;
	}

	public void setMaxWebhookConnections(Integer maxWebhookConnections) {
		this.maxWebhookConnections = maxWebhookConnections;
	}

	public List<String> getAllowedUpdates() {
		return allowedUpdates;
	}

	public void setAllowedUpdates(List<String> allowedUpdates) {
		this.allowedUpdates = allowedUpdates;
	}

	public ExponentialBackOff getExponentialBackOff() {
		return exponentialBackOff;
	}

	/**
	 * @implSpec Default implementation assumes starting at 500ms and max time of 60
	 *           minutes
	 * @param exponentialBackOff ExponentialBackOff to be used when long polling
	 *                           fails
	 */
	public void setExponentialBackOff(ExponentialBackOff exponentialBackOff) {
		this.exponentialBackOff = exponentialBackOff;
	}

	public ProxyType getProxyType() {
		return proxyType;
	}

	public void setProxyType(ProxyType proxyType) {
		this.proxyType = proxyType;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}
}
