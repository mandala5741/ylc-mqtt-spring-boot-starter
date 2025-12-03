package com.cqcloud.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cqcloud.platform.handler.NettyServerFactory;
import com.cqcloud.platform.service.NettyService;

import io.netty.bootstrap.ServerBootstrap;

/**
 * Netty服务自动配置
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
@Configuration
@ConditionalOnClass(ServerBootstrap.class)
@EnableConfigurationProperties(NettyServerProperties.class)
@ConditionalOnProperty(prefix = "netty.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NettyServerAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public NettyServerFactory nettyServerFactory(NettyServerProperties properties, NettyService nettyService) {
		return new NettyServerFactory(properties, nettyService);
	}

}