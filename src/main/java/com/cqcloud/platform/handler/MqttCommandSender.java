package com.cqcloud.platform.handler;

import cn.hutool.core.lang.func.VoidFunc0;
import cn.hutool.extra.spring.SpringUtil;
import com.cqcloud.platform.service.NettyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Random;

/**
 * MQTT 命令发送类
 *
 * @author weimeilayer@gmail.com ✨
 * @date 💓💕 2025年9月1日 🐬🐇 💓💕
 */
@Slf4j
public class MqttCommandSender {

	public static void sendIoOutput(String camId, String ioNum, String action) {
		NettyService nettyService = SpringUtil.getBean(NettyService.class);
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode gpio = mapper.createObjectNode();
			gpio.put("ioNum", ioNum).put("action", action);

			ObjectNode msg = mapper.createObjectNode();
			msg.put("cmd", "ioOutput")
				.put("msgId", generateMessageId())
				.put("utcTs", System.currentTimeMillis() / 1000)
				.set("gpioData", gpio);
			nettyService.sendToDevice(camId, msg.toString());
		}
		catch (Exception e) {
			log.error("发送命令失败：{}", e.getMessage());
		}
	}

	/**
	 * 发送 RS485 显示数据
	 * @param camId
	 * @param dataList
	 */
	public static void sendRs485Display(String camId, List<String> dataList) {
		NettyService nettyService = SpringUtil.getBean(NettyService.class);
		try {
			ObjectMapper mapper = new ObjectMapper();
			ArrayNode chn1Data = mapper.createArrayNode();

			for (String data : dataList) {
				ObjectNode dataNode = mapper.createObjectNode();
				dataNode.put("data", data);
				chn1Data.add(dataNode);
			}
			ObjectNode msg = mapper.createObjectNode();
			msg.put("cmd", "rs485Transmit")
				.put("msgId", generateMessageId())
				.put("utcTs", System.currentTimeMillis() / 1000)
				.put("encodeType", "hex2string")
				.set("chn1Data", chn1Data);
			nettyService.sendToDevice(camId, msg.toString());
		}
		catch (Exception e) {
			log.error("发送命令失败：{}", e.getMessage());
		}
	}

	/**
	 * 生成消息ID
	 * @return 生成的消息ID
	 */
	private static String generateMessageId() {
		long millis = System.currentTimeMillis();
		String random = String.format("%07d", new Random().nextInt(10000000));
		return millis + random;
	}

	/**
	 * 将中文字符串转换为 GBK 编码的十六进制字符串
	 * @param input 要转换的中文字符串
	 * @return GBK 编码对应的十六进制字符串，如果编码失败则返回空字符串
	 */
	public static String stringToHex(String input) {
		try {
			// 1. 将字符串转换为 GBK 编码的字节数组
			byte[] bytes = input.getBytes("GBK");

			// 2. 将字节数组转换为十六进制字符串
			StringBuilder hexString = new StringBuilder();
			for (byte b : bytes) {
				// 将每个字节转换为两位十六进制，并确保是大写
				String hex = String.format("%02X", b);
				hexString.append(hex);
			}
			return hexString.toString();
		}
		catch (UnsupportedEncodingException e) {
			System.err.println("不支持 GBK 编码: " + e.getMessage());
			return ""; // 或者抛出异常
		}
	}

}