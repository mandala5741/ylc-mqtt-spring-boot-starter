package com.cqcloud.platform.service;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;

import java.util.List;

/**
 * @author weimeilayer@gmail.com
 * @date 💓💕2024年9月8日🐬🐇💓💕
 */
public interface NettyService {

	/**
	 * 处理消息
	 * @param camId
	 * @param jsonMessage
	 * @return
	 */
	List<String> handleMessage(String camId, String jsonMessage);

	/**
	 * 新增MQTT处理方法
	 */
	void handleMqttMessage(ChannelHandlerContext ctx, MqttMessage msg);

	/**
	 * 处理通道不活跃
	 * @param ctx
	 */
	void handleChannelInactive(ChannelHandlerContext ctx);

	/**
	 * 处理异常
	 * @param ctx
	 * @param cause
	 */
	void handleException(ChannelHandlerContext ctx, Throwable cause);

	/**
	 * 手动主动发送消息
	 * @param camId
	 * @param string
	 */
	void sendToDevice(String camId, String string);

}
