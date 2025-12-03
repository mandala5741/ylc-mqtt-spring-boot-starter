package com.cqcloud.platform.handler;

import io.netty.channel.*;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.cqcloud.platform.config.NettyServerProperties;
import com.cqcloud.platform.service.NettyService;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Netty服务工厂类
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
public class NettyServerFactory implements InitializingBean, DisposableBean {

	private final NettyServerProperties properties;

	private final NettyService nettyService;

	private EventLoopGroup bossGroup;

	private EventLoopGroup workerGroup;

	private ChannelFuture channelFuture;

	public NettyServerFactory(NettyServerProperties properties, NettyService nettyService) {
		this.properties = properties;
		this.nettyService = nettyService;
	}

	/**
	 * 启动Netty服务
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		if (!properties.isEnabled()) {
			return;
		}
		// 配置线程组
		bossGroup = properties.getBossThreads() > 0 ? new NioEventLoopGroup(properties.getBossThreads())
				: new NioEventLoopGroup();

		workerGroup = properties.getWorkerThreads() > 0 ? new NioEventLoopGroup(properties.getWorkerThreads())
				: new NioEventLoopGroup();

		try {
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_RCVBUF, 30 * 1024 * 1024) // 系统接收缓冲区 2MB
				.childOption(ChannelOption.SO_RCVBUF, 30 * 1024 * 1024) // 子通道接收缓冲区 2MB
				.childOption(ChannelOption.RCVBUF_ALLOCATOR,
						new AdaptiveRecvByteBufAllocator(64, 512 * 1024, 30 * 1024 * 1024)) // 自适应缓冲区
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) {
						ChannelPipeline pipeline = ch.pipeline();
						// 添加字符串编解码器
						// pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
						// pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));

						// 添加MQTT解码器（核心：自动解析MQTT帧结构，处理分片）
						// 参数：最大消息长度（根据图片大小调整，如20MB）
						pipeline.addLast("mqttDecoder", new MqttDecoder(30 * 1024 * 1024));
						// 添加MQTT编码器（发送响应时自动编码为MQTT协议格式）
						pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);
						// 添加自定义处理器
						// 再添加您原有的业务处理器
						pipeline.addLast(new TcpMqttServerHandler(nettyService));
					}

				});

			channelFuture = bootstrap.bind(properties.getPort()).sync();
			System.out.println("Netty TCP Server started on port: " + properties.getPort());

			// 异步关闭监听
			channelFuture.channel().closeFuture().addListener(future -> {
				System.out.println("Netty TCP Server channel closed");
			});

		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Netty server start interrupted", e);
		}
		catch (Exception e) {
			destroy();
			throw new RuntimeException("Failed to start Netty server", e);
		}
	}

	/**
	 * 停止Netty服务
	 */
	@Override
	public void destroy() {
		if (channelFuture != null) {
			channelFuture.channel().close();
		}
		if (bossGroup != null) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}
		System.out.println("Netty TCP Server resources released");
	}

	/**
	 * 判断服务是否运行中
	 * @return
	 */
	public boolean isRunning() {
		return channelFuture != null && channelFuture.channel().isActive();
	}

}