package com.qinglan.example.device_point.server.handle;

import com.alibaba.fastjson2.JSONObject;
import com.qinglan.example.device_point.server.msg.ServerLBSInfo;
import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.server.util.SpringUtils;
import com.qinglan.example.device_point.ui.EventBus;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

@ChannelHandler.Sharable
@Slf4j
public class SetPropHandler extends SimpleChannelInboundHandler<ServerLBSInfo.SetPropResponse> {

    DeviceRegSession deviceRegSession = SpringUtils.getBean(DeviceRegSession.class);
    
    // 用于生成序列号的计数器
    private static final AtomicInteger seqCounter = new AtomicInteger(1);

    /**
     * 设置属性返回
     * Set Property Return
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.SetPropResponse msg) throws Exception {
        try {
            log.info("Received SetPropResponse: seq={}, result={}, errmsg='{}'", 
                     msg.getSeq(), msg.getResult(), msg.getErrmsg());
                     
            int type = 9; // 注意：这里使用9作为key前缀，虽然响应类型是10
            String channelId = ctx.channel().id().asLongText();
            String key = type + String.valueOf(channelId);
            JSONObject res = new JSONObject();
            res.put("result", msg.getResult());
            res.put("errmsg", msg.getErrmsg());
            res.put("seq", msg.getSeq());
            deviceRegSession.setReceiveMsg(key, res.toJSONString());
            
            // 获取设备ID并通知UI
            String deviceId = DeviceRegSession.getUidByChannelId(ctx.channel().id());
            if (deviceId != null) {
                String resultMsg = msg.getResult() == 0 ? "Success" : "Failed (code: " + msg.getResult() + ")";
                String message = "Set Property Response: " + resultMsg;
                if (!msg.getErrmsg().isEmpty()) {
                    message += ", Message: " + msg.getErrmsg();
                }
                
                EventBus.getInstance().postMessageReceived(deviceId, "RECV", message);
            }
        } catch (Exception e) {
            log.error("Error processing SetPropResponse", e);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
    
    /**
     * 发送设置设备属性请求
     * 
     * @param channel 设备通道
     * @param key 属性键
     * @param value 属性值
     * @return 生成的序列号
     */
    public static int sendSetPropertyRequest(Channel channel, String key, String value) {
        if (channel == null || !channel.isActive()) {
            log.warn("Cannot send property, channel is null or inactive");
            return -1;
        }
        
        try {
            // 生成唯一序列号
            int seq = seqCounter.getAndIncrement();
            
            // 构建SetDeviceProperty消息
            ServerLBSInfo.SetDeviceProperty.Builder builder = ServerLBSInfo.SetDeviceProperty.newBuilder();
            builder.setSeq(seq);
            builder.setKey(key);
            builder.setValue(value);
            
            ServerLBSInfo.SetDeviceProperty message = builder.build();
            byte[] data = message.toByteArray();
            
            // 构建ByteBuf并发送
            ByteBuf buffer = channel.alloc().buffer();
            // type = 9
            buffer.writeByte(9);
            buffer.writeBytes(data);
            channel.writeAndFlush(buffer);
            
            // 获取设备ID用于日志和UI通知
            String deviceId = DeviceRegSession.getUidByChannelId(channel.id());
            log.info("Sent SetDeviceProperty: deviceId={}, key={}, value={}, seq={}", 
                     deviceId != null ? deviceId : "unknown", key, value, seq);
            
            // 通知UI
            if (deviceId != null) {
                EventBus.getInstance().postMessageSent(
                    deviceId, 
                    "SEND", 
                    "Set Property: " + key + "=" + value + " (seq: " + seq + ")"
                );
            }
            
            return seq;
        } catch (Exception e) {
            log.error("Error sending SetDeviceProperty request", e);
            return -1;
        }
    }
    
    /**
     * 设置设备属性并等待响应
     * 
     * @param deviceId 设备ID
     * @param key 属性键
     * @param value 属性值
     * @return 是否成功
     */
    public static boolean setDeviceProperty(String deviceId, String key, String value) {
        DeviceRegSession session = SpringUtils.getBean(DeviceRegSession.class);
        Channel channel = session.isReg(deviceId);
        
        if (channel == null) {
            log.warn("Device not connected: {}", deviceId);
            return false;
        }
        
        try {
            // 发送请求
            int seq = sendSetPropertyRequest(channel, key, value);
            if (seq < 0) {
                return false;
            }
            
            // 准备接收响应
            String responseKey = 9 + channel.id().asLongText();
            session.initReceiveMsg(responseKey);
            
            // 等待响应
            String response = session.waitReceiveMsg(responseKey);
            if (response == null) {
                log.warn("Set property timeout: {}, key={}, value={}", deviceId, key, value);
                return false;
            }
            
            // 解析响应
            JSONObject responseJson = JSONObject.parseObject(response);
            int result = responseJson.getIntValue("result");
            
            log.info("Set property result: {}, deviceId={}, key={}, value={}, result={}", 
                     result == 0 ? "Success" : "Failed", deviceId, key, value, result);
            
            return result == 0;
        } catch (Exception e) {
            log.error("Error setting device property: deviceId={}, key={}, value={}", 
                      deviceId, key, value, e);
            return false;
        }
    }
    
    /**
     * 批量设置设备属性
     * 
     * @param deviceId 设备ID
     * @param properties 属性映射
     * @return 是否全部成功
     */
    public static boolean setDeviceProperties(String deviceId, Map<String, String> properties) {
        boolean allSuccess = true;
        
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            boolean success = setDeviceProperty(deviceId, entry.getKey(), entry.getValue());
            if (!success) {
                allSuccess = false;
                log.warn("Failed to set property: {}, deviceId={}", entry.getKey(), deviceId);
            }
            
            // 增加小延迟避免设备响应不过来
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return allSuccess;
    }
}