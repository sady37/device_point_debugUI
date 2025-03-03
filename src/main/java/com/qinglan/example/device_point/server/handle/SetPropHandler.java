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
import java.util.*;

@ChannelHandler.Sharable
@Slf4j
public class SetPropHandler extends SimpleChannelInboundHandler<ServerLBSInfo.SetPropResponse> {
    
    /**
     * 需要重启雷达的属性
     */
    private static final Set<String> RESTART_REQUIRED_PROPS = new HashSet<>(Arrays.asList(
        "radar_install_height", 
        "rectangle"
    ));

    /**
     * 发送设置设备属性请求
     * 
     * @param deviceId 设备ID
     * @param key 属性键
     * @param value 属性值
     * @return 是否成功发送请求（不代表设置成功）
     */
    public static boolean setProperty(String deviceId, String key, String value) {
        // 获取设备连接
        DeviceRegSession session = SpringUtils.getBean(DeviceRegSession.class);
        Channel channel = session.isReg(deviceId);
        
        if (channel == null || !channel.isActive()) {
            log.warn("Device not connected: {}", deviceId);
            return false;
        }
        
        try {
            // 构建SetDeviceProperty消息
            ServerLBSInfo.SetDeviceProperty.Builder builder = ServerLBSInfo.SetDeviceProperty.newBuilder();
            builder.setSeq(9); // seq固定为9（消息类型）
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
            
            // 记录日志
            log.info("Property setting request sent: Device={}, Property={}, Value={}", deviceId, key, value);
            
            // 通知UI
            EventBus.getInstance().postMessageSent(
                deviceId, 
                "SEND", 
                String.format("Set Property: %s=%s", key, value)
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send property setting request: Device={}, Property={}, Value={}", deviceId, key, value, e);
            return false;
        }
    }

    /**
     * 处理设备返回的属性设置响应
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.SetPropResponse msg) {
        try {
            // 获取设备ID
            String deviceId = DeviceRegSession.getUidByChannelId(ctx.channel().id());
            if (deviceId == null) {
                log.warn("Received property setting response from unknown device");
                return;
            }
            
            // 获取结果信息
            int result = msg.getResult();
            String resultDesc = getResultDescription(result);
            String errorMsg = msg.getErrmsg();
            
            // 记录日志
            if (result == 0) {
                log.info("Property setting successful: Device={}, Result={}", deviceId, resultDesc);
            } else {
                log.warn("Property setting failed: Device={}, Result={}, Error={}", deviceId, resultDesc, errorMsg);
            }
            
            // 构建详细消息
            String message = String.format(
                "Property Setting %s: Result=%d (%s)%s", 
                result == 0 ? "Successful" : "Failed",
                result, 
                resultDesc, 
                errorMsg.isEmpty() ? "" : ", Error: " + errorMsg
            );
            
            // 通知UI
            EventBus.getInstance().postMessageReceived(deviceId, "RECV", message);
        } catch (Exception e) {
            log.error("Error processing property setting response", e);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * 获取结果描述
     */
    private String getResultDescription(int resultCode) {
        switch (resultCode) {
            case 0: return "Success";
            case -1: return "Internal Error";
            case 1: return "Invalid Parameter";
            case 2: return "Permission Denied";
            case 3: return "Device Busy";
            default: return "Unknown Error (" + resultCode + ")";
        }
    }
    
    /**
     * 发送设备重启命令
     * 
     * @param deviceId 设备ID
     * @return 是否成功发送重启命令
     */
    public static boolean restartDevice(String deviceId) {
        // 获取设备连接
        DeviceRegSession session = SpringUtils.getBean(DeviceRegSession.class);
        Channel channel = session.isReg(deviceId);
        
        if (channel == null || !channel.isActive()) {
            log.warn("Device not connected for restart: {}", deviceId);
            return false;
        }
        
        try {
            // 构建重启消息 (CommonMessage)
            ServerLBSInfo.CommonMessage.Builder builder = ServerLBSInfo.CommonMessage.newBuilder();
            builder.setSeq(24); // 重启设备消息类型
            
            ServerLBSInfo.CommonMessage message = builder.build();
            byte[] data = message.toByteArray();
            
            // 构建ByteBuf并发送
            ByteBuf buffer = channel.alloc().buffer();
            buffer.writeByte(24); // 重启设备命令类型
            buffer.writeBytes(data);
            channel.writeAndFlush(buffer);
            
            // 记录日志
            log.info("Restart command sent to device: {}", deviceId);
            
            // 通知UI
            EventBus.getInstance().postMessageSent(
                deviceId, 
                "SEND", 
                "Restart device command"
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send restart command to device: {}", deviceId, e);
            return false;
        }
    }
}