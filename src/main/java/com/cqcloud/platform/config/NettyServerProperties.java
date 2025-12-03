package com.cqcloud.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Netty服务端属性配置
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
@ConfigurationProperties(prefix = "netty.server")
public class NettyServerProperties {

	private int port = 1883;

	private boolean enabled = true;

	private int bossThreads = 1;

	private int workerThreads = 0; // 0表示使用默认

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getBossThreads() {
		return bossThreads;
	}

	public void setBossThreads(int bossThreads) {
		this.bossThreads = bossThreads;
	}

	public int getWorkerThreads() {
		return workerThreads;
	}

	public void setWorkerThreads(int workerThreads) {
		this.workerThreads = workerThreads;
	}

}