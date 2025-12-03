package com.cqcloud.platform.handler;

import com.cqcloud.platform.service.NettyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Netty TCP服务器入口点 实际启动由NettyServerFactory处理
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
@Component
public class NettyTcpHandler {

	private final NettyService nettyService;

	@Autowired
	public NettyTcpHandler(NettyService nettyService) {
		this.nettyService = nettyService;
	}

	/**
	 * 保持原有的静态方法兼容性
	 */
	public static void start() {
		System.out.println("Netty server is now managed by Spring Boot auto-configuration");
		System.out.println("Please configure netty.server properties in application.yml");
	}

	public NettyService getNettyService() {
		return nettyService;
	}

}
