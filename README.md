# Netty MQTT 服务实现文档

## 概述
本项目基于 Netty 实现了一个 MQTT 服务器，用于处理设备连接、消息收发和业务逻辑处理。服务器默认监听 1883 端口，支持 MQTT 协议的基本功能。

## 配置信息

### 配置文件
```yaml
netty:
  server:
    enabled: true
    port: 1883      # 默认端口 1883
    boss-threads: 2
    worker-threads: 8
```

### 项目依赖
```xml
<!-- RS485协议加密发送相机内容 -->
<dependency>
    <groupId>cn.cqylc.platform</groupId>
    <artifactId>ylc-smart-spring-boot-starter</artifactId>
    <version>1.0.4</version>
</dependency>

<!-- MQTT服务接收 -->
<dependency>
    <groupId>cn.cqylc.platform</groupId>
    <artifactId>ylc-mqtt-spring-boot-starter</artifactId>
    <version>1.0.4</version>
</dependency>
```

## 功能特性

### 核心功能
1. **MQTT协议支持**
    - CONNECT 连接处理
    - PUBLISH 消息发布/订阅
    - SUBSCRIBE 订阅管理
    - PINGREQ/PINGRESP 心跳
    - DISCONNECT 断开连接
    - LWT（遗嘱消息）处理

2. **设备管理**
    - 设备连接状态维护
    - Channel 与 camId 绑定映射
    - 设备离线自动清理

3. **消息处理**
    - JSON 格式消息解析
    - 心跳消息自动回复
    - 业务消息分发处理
    - 异常消息过滤

### 业务功能
1. **主动下发控制**
    - IO 输出控制
    - RS485 数据透传
    - LCD 显示屏控制
    - 语音播报

2. **消息响应**
    - 设备心跳响应
    - 错误响应生成
    - 多响应消息支持

## 核心类：NettyServiceImpl

### 主要成员变量
```java
// MQTT相关静态成员
private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
private static final ConcurrentMap<String, Channel> CAM_CHANNEL_MAP = new ConcurrentHashMap<>();
private static final AttributeKey<String> CAM_ID_KEY = AttributeKey.valueOf("camId");
private static final AttributeKey<MqttConnectMessage> CONNECT_MESSAGE_KEY = AttributeKey.valueOf("connectMessage");
private static final Pattern DEVICE_TOPIC_PATTERN = Pattern.compile("^/device/([^/]+)/.*$");
```

### 核心方法说明

#### 1. MQTT消息处理入口
```java
public void handleMqttMessage(ChannelHandlerContext ctx, MqttMessage msg)
```
- 根据消息类型分发到不同的处理方法
- 支持的消息类型：CONNECT、PINGREQ、PUBLISH、DISCONNECT、SUBSCRIBE

#### 2. 设备连接处理
```java
private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage connectMsg)
```
- 解析客户端连接信息
- 保存连接消息用于LWT处理
- 返回CONNACK确认连接

#### 3. 消息发布处理
```java
private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage publishMsg)
```
- 解析主题和负载数据
- 提取JSON格式消息
- 区分心跳消息和业务消息
- 调用业务处理方法并返回响应

#### 4. 订阅请求处理
```java
private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage subscribeMsg)
```
- 解析订阅请求
- 强制使用QoS=0（本项目仅支持QoS=0）
- 返回SUBACK确认

#### 5. 设备通道绑定
```java
private void bindCamIdIfNecessary(ChannelHandlerContext ctx, String topic)
```
- 从主题中提取camId（格式：/device/{camId}/...）
- 建立camId与Channel的映射关系
- 管理设备连接状态

#### 6. 心跳处理
```java
private void replyHeartbeat(ChannelHandlerContext ctx, JsonNode req)
```
- 生成心跳响应消息
- 保持设备连接活跃
- 确认消息ID对应

#### 7. 通道状态管理
```java
public void handleChannelInactive(ChannelHandlerContext ctx)
```
- 设备断开连接时清理映射
- 触发LWT（遗嘱消息）处理
- 发布离线通知

#### 8. 异常处理
```java
public void handleException(ChannelHandlerContext ctx, Throwable cause)
```
- 记录异常日志
- 关闭异常通道
- 防止服务崩溃

### 主动下发方法

#### 1. 设备消息下发
```java
@Override
public void sendToDevice(String camId, String payload)
```
- 根据camId查找设备通道
- 验证通道活跃状态
- 构造MQTT PUBLISH消息
- 向指定设备发送消息

#### 2. IO输出控制
```java
public void sendIoOutput(String camId, String ioNum, String action)
```
- 生成IO控制指令
- 支持on/off操作
- 符合文档8.1.1格式

#### 3. RS485数据透传
```java
private String generateRs485TransmitCommand(String originalMsgId, List<String> dataList, int channel)
```
- 支持多个数据批量发送
- 可配置通道1或2
- 支持hex2string编码

#### 4. LCD显示控制
```java
private String generateChargeCommand(String plateNum, String originalMsgId)
```
- 多行文本显示
- 车牌号高亮
- 支持二维码生成
- 语音播报联动

### 工具方法

#### 1. 消息ID生成
```java
private static String generateMessageId()
```
- 20位唯一ID：13位毫秒时间戳 + 7位随机数
- 符合文档格式要求

#### 2. 文本转十六进制
```java
private static String textToHexString(String text)
```
- 使用GBK编码
- 支持中文字符
- 返回大写十六进制字符串

#### 3. JSON提取
```java
private static String extractJson(String s)
```
- 从字符串中提取完整JSON
- 处理包裹字符
- 返回有效JSON字符串

## 测试接口

### 主动下发测试
```java
@PostMapping("/testMqtt")
public String testMqtt(@RequestBody MqttPushDto dto) {
    String camId = Optional.ofNullable(dto.getCamId())
        .filter(s -> !s.trim().isEmpty())
        .filter(s -> !s.isEmpty())
        .orElse("180300xxxxx");
    
    String iotNum = Optional.ofNullable(dto.getIoNum())
        .filter(s -> !s.trim().isEmpty())
        .filter(s -> !s.isEmpty())
        .orElse("io1");
    
    // 接口手动下发消息
    nettyService.sendIoOutput(camId, iotNum, "on");
    return "hello world";
}
```

## 消息格式

### 设备主题格式
```
/device/{camId}/{subtopic}
```
示例：`/device/18030012345/get`

### 心跳消息格式
```json
{
  "cmd": "heartbeat",
  "msgId": "16094592000001234567",
  "status": "ok"
}
```

### 心跳响应格式
```json
{
  "cmd": "heartbeatRsp",
  "msgId": "16094592000001234567",
  "status": "ok"
}
```

### IO输出指令格式
```json
{
  "cmd": "ioOutput",
  "msgId": "16094592000001234567",
  "utcTs": 1609459200,
  "gpioData": {
    "ioNum": "io1",
    "action": "on"
  }
}
```

### RS485透传指令格式
```json
{
  "cmd": "rs485Transmit",
  "msgId": "16094592000001234567",
  "utcTs": 1609459200,
  "encodeType": "hex2string",
  "chn1Data": [
    {
      "data": "48656C6C6F"
    }
  ]
}
```
```
## 自己继承实现的类
package com.cqcloud.platform.service.impl;

import com.cqcloud.platform.service.NettyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.AttributeKey;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* Netty服务实现类 - 包含完整的MQTT处理逻辑
* @author weimeilayer@gmail.com
* @date 💓💕2024年9月8日🐬🐇💓💕
  */
  @Slf4j
  @Service
  @AllArgsConstructor
  public class NettyServiceImpl implements NettyService {

// ==================== MQTT相关静态成员 ====================
private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
private static final ConcurrentMap<String, Channel> CAM_CHANNEL_MAP = new ConcurrentHashMap<>();
private static final AttributeKey<String> CAM_ID_KEY = AttributeKey.valueOf("camId");
private static final AttributeKey<MqttConnectMessage> CONNECT_MESSAGE_KEY = AttributeKey.valueOf("connectMessage");
private static final Pattern DEVICE_TOPIC_PATTERN = Pattern.compile("^/device/([^/]+)/.*$");

@Override
public List<String> handleMessage(String camId, String message) {
//System.out.println("Service层处理消息: " + message);
JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
try {
String cmd = jsonMessage.get("cmd").getAsString();
String deviceId = jsonMessage.get("devId").getAsString();
System.out.println("解析到命令: " + cmd + ", 设备ID: " + deviceId);
// ============================ 业务处理区域=============
} catch (Exception e) {
System.out.println("消息不是JSON格式" + message);
}
// 如果移除 返回默认心跳消息
return handleHeartbeat(jsonMessage, camId);
}

// ==================== MQTT消息处理方法 ====================

/**
* 处理MQTT消息
  */
  public void handleMqttMessage(ChannelHandlerContext ctx, MqttMessage msg) {
  System.out.println("时间：" + LocalDateTime.now() + "  [MQTT] 收到消息类型: " + msg.fixedHeader().messageType());

switch (msg.fixedHeader().messageType()) {
case CONNECT:
handleConnect(ctx, (MqttConnectMessage) msg);
break;
case PINGREQ:
ctx.writeAndFlush(new MqttMessage(
new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)
));
break;
case PUBLISH:
handlePublish(ctx, (MqttPublishMessage) msg);
break;
case DISCONNECT:
ctx.close();
break;
case SUBSCRIBE:
handleSubscribe(ctx, (MqttSubscribeMessage) msg);
break;
default:
System.out.println("时间：" + LocalDateTime.now() + "  [MQTT] 忽略不支持的消息类型: " + msg.fixedHeader().messageType());
break;
}
}

/**
* 处理设备 CONNECT 连接请求
  */
  private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage connectMsg) {
  String clientId = connectMsg.payload().clientIdentifier();
  boolean hasWill = connectMsg.variableHeader().isWillFlag();
  String willTopic = hasWill ? connectMsg.payload().willTopic() : "N/A";

System.out.println("时间：" + LocalDateTime.now() + "  [CONN] 设备尝试连接 - clientId: " + clientId + ", 含遗嘱: " + hasWill + ", 遗嘱主题: " + willTopic);

// 保存 CONNECT 消息供后续 LWT 使用
ctx.channel().attr(CONNECT_MESSAGE_KEY).set(connectMsg);

// 回复 CONNACK
ctx.writeAndFlush(new MqttConnAckMessage(
new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false)
));

System.out.println("时间：" + LocalDateTime.now() + "  [CONN] 已接受设备连接 - clientId: " + clientId);
}

/**
* 处理设备 PUBLISH 消息
  */
  private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage publishMsg) {
  // 解析主题
  String topic = publishMsg.variableHeader().topicName();
  // 解析负载
  ByteBuf payload = publishMsg.payload();
  // 解析 JSON
  byte[] bytes = new byte[payload.readableBytes()];
  // 读取字节
  payload.readBytes(bytes);
  // 解析 JSON
  String rawPayload = new String(bytes, StandardCharsets.UTF_8);
  // 绑定 camId
  bindCamIdIfNecessary(ctx, topic);
  // 提取 JSON
  String json = extractJson(rawPayload);
  if (json == null) {
  System.err.println("[ERROR] 无效 JSON 负载，丢弃消息 - 内容: " + rawPayload);
  return;
  }
  try {
  // 解析 JSON
  JsonNode root = JSON_MAPPER.readTree(json);
  // 命令
  String cmd = root.path("cmd").asText("");
  // camId
  String camId = ctx.channel().attr(CAM_ID_KEY).get();
  // 心跳
  if ("heartbeat".equals(cmd)) {
  System.out.println("时间：" + LocalDateTime.now() + "  [HEARTBEAT] 收到心跳请求 - camId: " + camId + ", msgId: " + root.path("msgId").asText(""));
  // 回复心跳
  replyHeartbeat(ctx, root);
  return;
  }
  System.out.println("时间：" + LocalDateTime.now() + "  [BUSINESS] 处理业务消息 - camId: " + camId + ", cmd: " + cmd);
  List<String> responses = this.handleMessage(camId, json);
  // 响应数据接口
  if (responses != null && !responses.isEmpty()) {
  System.out.println("时间：" + LocalDateTime.now() + "  [RESPONSE] 业务层返回 " + responses.size() + " 条响应，准备下发");
  for (String resp : responses) {
  // 业务发送响应消息
  sendToDevice(camId, resp);
  }
  }
  } catch (Exception e) {
  System.err.println("[EXCEPTION] 处理 PUBLISH 消息异常 - 主题: " + topic + ", 错误: " + e.getMessage());
  }
  }

/**
* 处理 SUBSCRIBE 请求（仅回复 SUBACK，不实际维护订阅关系）
  */
  private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage subscribeMsg) {
  MqttMessageIdVariableHeader messageIdVarHeader = subscribeMsg.variableHeader();
  int messageId = messageIdVarHeader.messageId();

// 获取所有订阅的主题
List<MqttTopicSubscription> topicSubscriptions = subscribeMsg.payload().topicSubscriptions();
System.out.println("时间：" + LocalDateTime.now() + "  [SUBSCRIBE] 收到订阅请求 - messageId: " + messageId);

for (MqttTopicSubscription subscription : topicSubscriptions) {
String topic = subscription.topicName();
MqttQoS qos = subscription.qualityOfService();
System.out.println("时间：" + LocalDateTime.now() + "  [SUBSCRIBE] 客户端请求订阅主题: " + topic + ", QoS: " + qos.value());
}

// 构造 SUBACK 响应（全部授予 QoS=0，因本服务仅支持 QoS=0）
int[] grantedQos = new int[topicSubscriptions.size()];
for (int i = 0; i < grantedQos.length; i++) {
// 强制降级为 QoS=0（与你的 publish 一致）
grantedQos[i] = 0;
}

MqttSubAckPayload subAckPayload = new MqttSubAckPayload(grantedQos);
MqttSubAckMessage subAckMessage = new MqttSubAckMessage(
new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
messageIdVarHeader,
subAckPayload
);

ctx.writeAndFlush(subAckMessage);
System.out.println("时间：" + LocalDateTime.now() + "  [SUBSCRIBE] 已回复 SUBACK - messageId: " + messageId);
}

/**
* 绑定 camId 到 Channel
  */
  private void bindCamIdIfNecessary(ChannelHandlerContext ctx, String topic) {
  String existing = ctx.channel().attr(CAM_ID_KEY).get();
  if (existing != null) {
  // 已绑定，无需重复操作
  return;
  }
  Matcher m = DEVICE_TOPIC_PATTERN.matcher(topic);
  if (m.matches()) {
  String camId = m.group(1);
  ctx.channel().attr(CAM_ID_KEY).set(camId);
  CAM_CHANNEL_MAP.put(camId, ctx.channel());
  System.out.println("时间：" + LocalDateTime.now() + "  [BIND] 成功绑定 camId 到通道 - camId: " + camId);
  } else {
  System.out.println("时间：" + LocalDateTime.now() + "  [WARN] 无法从主题提取 camId - 主题: " + topic);
  }
  }

/**
* 回复心跳
  */
  private void replyHeartbeat(ChannelHandlerContext ctx, JsonNode req) {
  ObjectNode rsp = JSON_MAPPER.createObjectNode();
  rsp.put("cmd", "heartbeatRsp");
  rsp.put("msgId", req.path("msgId").asText(""));
  rsp.put("status", "ok");

String camId = ctx.channel().attr(CAM_ID_KEY).get();
if (camId != null) {
// 发送心跳响应
sendToDevice(camId, rsp.toString());
} else {
System.err.println("[ERROR] 心跳回复失败：camId 未绑定");
}
}

/**
* 处理通道非活跃状态（断开连接）
  */
  public void handleChannelInactive(ChannelHandlerContext ctx) {
  String camId = ctx.channel().attr(CAM_ID_KEY).get();
  if (camId != null) {
  CAM_CHANNEL_MAP.remove(camId);
  System.out.println("时间：" + LocalDateTime.now() + "  [DISCONNECT] 设备通道非活跃，已移除映射 - camId: " + camId);
  } else {
  System.out.println("时间：" + LocalDateTime.now() + "  [DISCONNECT] 通道关闭，但未绑定 camId");
  }

// 检查是否需要触发遗嘱
MqttConnectMessage connectMsg = ctx.channel().attr(CONNECT_MESSAGE_KEY).get();
if (connectMsg != null && connectMsg.variableHeader().isWillFlag()) {
String willTopic = connectMsg.payload().willTopic();
byte[] willMessage = connectMsg.payload().willMessageInBytes();
String willPayloadStr = new String(willMessage, StandardCharsets.UTF_8);

System.out.println("时间：" + LocalDateTime.now() + "  [LWT] 检测到遗嘱标志，准备发布遗嘱 - 主题: " + willTopic);

Matcher m = DEVICE_TOPIC_PATTERN.matcher(willTopic);
if (m.matches()) {
String extractedCamId = m.group(1);
System.out.println("时间：" + LocalDateTime.now() + "  [LWT] 遗嘱 camId 提取成功: " + extractedCamId + "，内容: " + willPayloadStr);

// 构造遗嘱消息（QoS=0）
MqttPublishMessage willPublish = new MqttPublishMessage(
new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE,
connectMsg.variableHeader().isWillRetain(), willMessage.length),
new MqttPublishVariableHeader(willTopic, 0),
Unpooled.copiedBuffer(willMessage)
);

System.out.println("时间：" + LocalDateTime.now() + "  [LWT] 遗嘱消息构造完成，需通过其他方式广播（当前仅打印）");
System.out.println("时间：" + LocalDateTime.now() + "  [LWT][CONTENT] " + willPayloadStr);
} else {
System.err.println("[LWT][ERROR] 遗嘱主题格式非法，拒绝发布 - 主题: " + willTopic);
}
} else {
System.out.println("时间：" + LocalDateTime.now() + "  [LWT] 无遗嘱设置或连接信息缺失，跳过遗嘱发布");
}
}

/**
* 处理通道异常
  */
  public void handleException(ChannelHandlerContext ctx, Throwable cause) {
  String camId = ctx.channel().attr(CAM_ID_KEY).get();
  System.err.println("[EXCEPTION] 通道异常 - camId: " + camId + ", 原因: " + cause.getMessage());
  cause.printStackTrace();
  ctx.close();
  }

// ==================== 主动下发方法 ====================

/**
* 主动向设备发送消息
* @param camId
* @param payload
  */
  @Override
  public void sendToDevice(String camId, String payload) {
  Channel channel = CAM_CHANNEL_MAP.get(camId);
  if (channel == null || !channel.isActive()) {
  System.err.println("[SEND][ERROR] 目标设备离线或通道无效 - camId: " + camId);
  return;
  }
  String topic = "/device/" + camId + "/get";
  byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
  MqttPublishMessage msg = new MqttPublishMessage(
  new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, bytes.length),
  new MqttPublishVariableHeader(topic, 0),
  Unpooled.copiedBuffer(bytes)
  );
  channel.writeAndFlush(msg);
  System.out.println("时间：" + LocalDateTime.now() + "  [SEND] 已向设备下发消息 - camId: " + camId + ", 主题: " + topic + ", 内容: " + payload);
  }

// ==================== 工具方法 ====================

private static String extractJson(String s) {
   int i = s.indexOf('{');
   int j = s.lastIndexOf('}');
   if (i >= 0 && j > i) {
   return s.substring(i, j + 1);
   }
   return null;
}

// ==================== 原有的业务处理方法 ====================
// （以下方法保持不变，只是添加了 @Override 注解）

/**
* 处理结果消息
  */
  private List<String> handlePlateResult(JsonObject message, String deviceId) {
  try {
  // ============================ 业务处理区域=============
  } catch (Exception e) {
  System.out.println("处理结果时出错: " + e.getMessage());
  }
  // 如果报错 返回默认心跳消息
  return handleHeartbeat(message, deviceId);
  }

/**
* 处理设备心跳消息
  */
  private List<String> handleHeartbeat(JsonObject message, String deviceId) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理心跳消息，设备: " + deviceId);
     String heartbeatResponse = generateHeartbeatResponse(message);
     responses.add(heartbeatResponse);
     return responses;
  }

/**
* 处理IO状态变化消息
  */
  private List<String> handleIoStatus(JsonObject message, String deviceId) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理IO状态变化，设备: " + deviceId);
     return responses;
  }

/**
* 处理RS485数据
  */
  private List<String> handleRs485Data(JsonObject message, String deviceId) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理RS485数据，设备: " + deviceId);
     return responses;
  }

/**
* 处理设备信息
  */
  private List<String> handleDeviceInfo(JsonObject message, String deviceId) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理设备信息，设备: " + deviceId);
     return responses;
  }

/**
* 处理未知命令的消息
  */
  private List<String> handleUnknownCommand(JsonObject message, String deviceId) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理未知命令，设备: " + deviceId);
     String errorResponse = generateErrorResponse(message, "unknown_command");
     responses.add(errorResponse);
     return responses;
  }

/**
* 处理原始消息并生成响应列表
  */
  private List<String> handleRawMessage(String message) {
     List<String> responses = new ArrayList<>();
     System.out.println("处理原始消息: " + message);
     return responses;
  }

// ============== 响应生成方法 ==============

/**
* 生成心跳响应（符合文档7.4.2格式）
  */
  private String generateHeartbeatResponse(JsonObject message) {
     String msgId = message.get("msgId").getAsString();
     JsonObject response = new JsonObject();
     response.addProperty("cmd", "heartbeatRsp");
     response.addProperty("msgId", msgId);
     response.addProperty("status", "ok");
     return response.toString();
  }

/**
* 生成开闸指令（符合文档8.1.1格式）
  */
  private static String generateOpenGateCommand(String originalMsgId) {
     long currentTime = System.currentTimeMillis() / 1000;
     JsonObject request = new JsonObject();
     request.addProperty("cmd", "ioOutput");
     request.addProperty("msgId", generateMessageId());
     request.addProperty("utcTs", currentTime);

   JsonObject gpioData = new JsonObject();
   gpioData.addProperty("ioNum", "io1");
   gpioData.addProperty("action", "on");
   
   request.add("gpioData", gpioData);
   System.out.println("生成开闸指令：" + request.toString());
   return request.toString();
}

/**
* 生成RS485透传指令（符合文档8.2.1格式）- 支持多个数据
  */
  private static String generateRs485TransmitCommand(String originalMsgId, List<String> dataList, int channel) {
     long currentTime = System.currentTimeMillis() / 1000;
     JsonObject request = new JsonObject();
     request.addProperty("cmd", "rs485Transmit");
     request.addProperty("msgId", generateMessageId());
     request.addProperty("utcTs", currentTime);
     request.addProperty("encodeType", "hex2string");

      JsonArray chnDataArray = new JsonArray();
      for (String data : dataList) {
      JsonObject dataObject = new JsonObject();
      dataObject.addProperty("data", data);
      chnDataArray.add(dataObject);
}

if (channel == 1) {
   request.add("chn1Data", chnDataArray);
   } else if (channel == 2) {
   request.add("chn2Data", chnDataArray);
   }
   System.out.println("生成RS485透传指令，通道" + channel + "，数据条数：" + dataList.size());
   return request.toString();
}

/**
* 生成显示屏多行显示指令（通过RS485透传）
  */
  private static String generateMultiLineDisplayCommand(List<String> displayTexts, int channel) {
  List<String> hexDataList = new ArrayList<>();
     for (String text : displayTexts) {
     String hexData = textToHexString(text);
     hexDataList.add(hexData);
  }
  return generateRs485TransmitCommand(null, hexDataList, channel);
  }

/**
* 生成单个数据的RS485透传指令（兼容旧版本）
  */
  private String generateRs485TransmitCommand(String originalMsgId, String data, int channel) {
     List<String> dataList = new ArrayList<>();
     dataList.add(data);
     return generateRs485TransmitCommand(originalMsgId, dataList, channel);
  }

/**
* 工具方法：文本转16进制字符串
  */
  private static String textToHexString(String text) {
     try {
        byte[] bytes = text.getBytes("GBK");
        StringBuilder hexBuilder = new StringBuilder();
     for (byte b : bytes) {
        hexBuilder.append(String.format("%02X", b));
     }
        return hexBuilder.toString();
     } catch (Exception e) {
        return "";
     }
  }

/**
* 生成LCD显示指令
  */
  private String generateChargeCommand(String plateNum, String originalMsgId) {
     long currentTime = System.currentTimeMillis() / 1000;
   
     JsonObject request = new JsonObject();
     request.addProperty("cmd", "lcdShowInfo");
     request.addProperty("msgId", generateMessageId());
     request.addProperty("utcTs", currentTime);
   
     JsonObject showInfo = new JsonObject();
     showInfo.addProperty("textType", "plateLine");
   
     JsonObject plateInfo = new JsonObject();
     plateInfo.addProperty("plateNum", plateNum);
     plateInfo.addProperty("textColor", "FF0000");
     showInfo.add("plateInfo", plateInfo);
   
     JsonArray lineInfo = new JsonArray();
     JsonObject line1 = new JsonObject();
     line1.addProperty("lineText", "请缴费5元");
     line1.addProperty("fontSize", "large");
     line1.addProperty("textColor", "FF0000");
     lineInfo.add(line1);
   
     showInfo.add("lineInfo", lineInfo);
     showInfo.addProperty("qrcodeUrl", "http://xxx.com/pay");
   
     request.add("showInfo", showInfo);
   
     JsonObject voiceInfo = new JsonObject();
     voiceInfo.addProperty("voiceText", "请缴费5元");
     request.add("voiceInfo", voiceInfo);
   
     return request.toString();
  }

/**
* 生成错误响应
  */
  private String generateErrorResponse(JsonObject message, String errorType) {
     String msgId = message.has("msgId") ? message.get("msgId").getAsString() : generateMessageId();
   
     JsonObject response = new JsonObject();
     response.addProperty("cmd", "errorRsp");
     response.addProperty("msgId", msgId);
     response.addProperty("status", errorType);
   
     return response.toString();
  }

/**
* 生成符合文档格式的消息ID（20位：13位毫秒时间+7位随机数）
  */
  private static String generateMessageId() {
     long millis = System.currentTimeMillis();
     String random = String.format("%07d", new Random().nextInt(10000000));
     return millis + random;
     }
  }
```

## 使用说明

### 1. 启动服务
- 启动Spring Boot应用
- Netty自动监听1883端口
- 等待设备连接

### 2. 设备连接
- 设备使用MQTT协议连接
- ClientID作为设备标识
- 支持遗嘱消息设置

### 3. 消息收发
- 设备订阅主题接收指令
- 服务发布主题发送指令
- 支持JSON格式消息

### 4. 业务扩展
- 在`handleMessage`方法中添加业务处理
- 使用`sendToDevice`方法主动下发
- 根据需要扩展消息类型

## 注意事项

1. **QoS限制**：当前仅支持QoS=0，订阅请求会被强制降级
2. **线程安全**：CAM_CHANNEL_MAP使用ConcurrentHashMap保证线程安全
3. **异常处理**：所有异常都被捕获并记录，防止服务崩溃
4. **资源清理**：设备断开时自动清理相关资源
5. **消息格式**：仅处理JSON格式消息，其他格式会被丢弃

## 性能优化建议

1. **连接管理**：定期清理无效连接
2. **内存优化**：监控Channel映射内存使用
3. **线程配置**：根据实际负载调整boss/worker线程数
4. **日志优化**：生产环境适当减少调试日志
5. **消息队列**：高并发场景考虑引入消息队列缓冲
