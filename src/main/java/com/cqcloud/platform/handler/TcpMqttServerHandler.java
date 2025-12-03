package com.cqcloud.platform.handler;

import com.cqcloud.platform.service.NettyService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT服务端处理器（代理到NettyService）
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
@Slf4j
public class TcpMqttServerHandler extends SimpleChannelInboundHandler<MqttMessage> {

	private final NettyService nettyService;

	public TcpMqttServerHandler(NettyService nettyService) {
		this.nettyService = nettyService;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
		// 将MQTT消息处理委托给NettyService
		nettyService.handleMqttMessage(ctx, msg);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		// 将通道非活跃处理委托给NettyService
		nettyService.handleChannelInactive(ctx);
		super.channelInactive(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		// 将异常处理委托给NettyService
		nettyService.handleException(ctx, cause);
	}

}