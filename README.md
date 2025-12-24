# YLC MQTT Spring Boot Starter

基于车牌识别 MQTT 解析的 Spring Boot Starter，启动项目后 Netty 服务会自动在 1883 端口启动。

## 目录

- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [依赖引入](#依赖引入)
- [使用示例](#使用示例)
- [自定义实现](#自定义实现)

## 快速开始

启动项目后，Netty MQTT 服务将自动在配置的端口（默认 1883）启动。

## 配置说明

在 `application.yml` 中添加以下配置（如果不配置则使用默认值 1883 端口）：

```yaml
netty:
  server:
    enabled: true
    port: 1883
    boss-threads: 2
    worker-threads: 8
```

## 依赖引入

### MQTT 服务依赖

```xml
<dependency>
    <groupId>cn.cqylc.platform</groupId>
    <artifactId>ylc-mqtt-spring-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

### RS485 协议加密发送依赖（可选）

如需使用 RS485 协议加密发送相机内容，请引入以下依赖：

```xml
<dependency>
    <groupId>cn.cqylc.platform</groupId>
    <artifactId>ylc-smart-spring-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

## 使用示例

### 主动下发消息 Controller

```java
private final NettyService nettyService;

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


## 自定义实现

创建自己的 `NettyService` 实现类来处理 MQTT 消息：

```java
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
        JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
        try {
            String cmd = jsonMessage.get("cmd").getAsString();
            String deviceId = jsonMessage.get("devId").getAsString();
            System.out.println("解析到命令: " + cmd + ", 设备ID: " + deviceId);
            // ============================ 业务处理区域 =============
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
        String topic = publishMsg.variableHeader().topicName();
        ByteBuf payload = publishMsg.payload();
        byte[] bytes = new byte[payload.readableBytes()];
        payload.readBytes(bytes);
        String rawPayload = new String(bytes, StandardCharsets.UTF_8);

        bindCamIdIfNecessary(ctx, topic);

        String json = extractJson(rawPayload);
        if (json == null) {
            System.err.println("[ERROR] 无效 JSON 负载，丢弃消息 - 内容: " + rawPayload);
            return;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            String cmd = root.path("cmd").asText("");
            String camId = ctx.channel().attr(CAM_ID_KEY).get();

            if ("heartbeat".equals(cmd)) {
                System.out.println("时间：" + LocalDateTime.now() + "  [HEARTBEAT] 收到心跳请求 - camId: " + camId + ", msgId: " + root.path("msgId").asText(""));
                replyHeartbeat(ctx, root);
                return;
            }
            System.out.println("时间：" + LocalDateTime.now() + "  [BUSINESS] 处理业务消息 - camId: " + camId + ", cmd: " + cmd);
            List<String> responses = this.handleMessage(camId, json);
            if (responses != null && !responses.isEmpty()) {
                System.out.println("时间：" + LocalDateTime.now() + "  [RESPONSE] 业务层返回 " + responses.size() + " 条响应，准备下发");
                for (String resp : responses) {
                    sendToDevice(camId, resp);
                }
            }
        } catch (Exception e) {
            System.err.println("[EXCEPTION] 处理 PUBLISH 消息异常 - 主题: " + topic + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 处理 SUBSCRIBE 请求
     */
    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage subscribeMsg) {
        MqttMessageIdVariableHeader messageIdVarHeader = subscribeMsg.variableHeader();
        int messageId = messageIdVarHeader.messageId();

        List<MqttTopicSubscription> topicSubscriptions = subscribeMsg.payload().topicSubscriptions();
        System.out.println("时间：" + LocalDateTime.now() + "  [SUBSCRIBE] 收到订阅请求 - messageId: " + messageId);

        for (MqttTopicSubscription subscription : topicSubscriptions) {
            String topic = subscription.topicName();
            MqttQoS qos = subscription.qualityOfService();
            System.out.println("时间：" + LocalDateTime.now() + "  [SUBSCRIBE] 客户端请求订阅主题: " + topic + ", QoS: " + qos.value());
        }

        int[] grantedQos = new int[topicSubscriptions.size()];
        for (int i = 0; i < grantedQos.length; i++) {
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
     * @param camId 设备ID
     * @param payload 消息内容
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

    // ==================== 业务处理方法 ====================

    private List<String> handleHeartbeat(JsonObject message, String deviceId) {
        List<String> responses = new ArrayList<>();
        System.out.println("处理心跳消息，设备: " + deviceId);
        String heartbeatResponse = generateHeartbeatResponse(message);
        responses.add(heartbeatResponse);
        return responses;
    }

    // ============== 响应生成方法 ==============

    private String generateHeartbeatResponse(JsonObject message) {
        String msgId = message.get("msgId").getAsString();
        JsonObject response = new JsonObject();
        response.addProperty("cmd", "heartbeatRsp");
        response.addProperty("msgId", msgId);
        response.addProperty("status", "ok");
        return response.toString();
    }

    private static String generateMessageId() {
        long millis = System.currentTimeMillis();
        String random = String.format("%07d", new Random().nextInt(10000000));
        return millis + random;
    }
}
```

## License

Apache License 2.0