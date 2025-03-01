package com.qinglan.example.device_point.server.session;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.qinglan.example.device_point.ui.DeviceSessionListener;
import com.qinglan.example.device_point.ui.EventBus;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DeviceRegSession {

    // Session listeners
    private static final List<DeviceSessionListener> sessionListeners = new ArrayList<>();
    
    // 缓存通道
    private static Map<String, Channel> regSession = new ConcurrentHashMap<>();

    private static Map<ChannelId, String> channelInfo = new ConcurrentHashMap<>();

    public DeviceRegSession() {
        // 构造函数保持不变
    }
    
    /**
     * 添加会话监听器
     * 
     * @param listener 监听器实例
     */
    public void addSessionListener(DeviceSessionListener listener) {
        sessionListeners.add(listener);
    }
    
    /**
     * 移除会话监听器
     * 
     * @param listener 监听器实例
     */
    public void removeSessionListener(DeviceSessionListener listener) {
        sessionListeners.remove(listener);
    }
    
    /**
     * 发送设备连接事件
     * 
     * @param uid 设备UID
     * @param channel 设备通道
     */
    private static void notifyDeviceConnected(String uid, Channel channel) {
        String ipAddress = getIpAddress(channel);
        
        // 通知UI
        EventBus.getInstance().postDeviceConnected(uid, ipAddress);
        
        // 通知所有监听器
        for (DeviceSessionListener listener : sessionListeners) {
            listener.onDeviceConnected(uid, ipAddress);
        }
    }
    
    /**
     * 发送设备断开连接事件
     * 
     * @param uid 设备UID
     */
    private static void notifyDeviceDisconnected(String uid) {
        // 通知UI
        EventBus.getInstance().postDeviceDisconnected(uid);
        
        // 通知所有监听器
        for (DeviceSessionListener listener : sessionListeners) {
            listener.onDeviceDisconnected(uid);
        }
    }
    
    /**
     * 发送消息接收事件
     * 
     * @param uid 设备UID
     * @param messageType 消息类型
     * @param message 消息内容
     */
    private void notifyMessageReceived(String uid, String messageType, String message) {
        // 通知UI
        EventBus.getInstance().postMessageReceived(uid, messageType, message);
        
        // 通知所有监听器
        for (DeviceSessionListener listener : sessionListeners) {
            listener.onMessageReceived(uid, messageType, message);
        }
    }
    
    /**
     * 发送消息发送事件
     * 
     * @param uid 设备UID
     * @param messageType 消息类型
     * @param message 消息内容
     */
    private static void notifyMessageSent(String uid, String messageType, String message) {
        // 通知UI
        EventBus.getInstance().postMessageSent(uid, messageType, message);
        
        // 通知所有监听器
        for (DeviceSessionListener listener : sessionListeners) {
            listener.onMessageSent(uid, messageType, message);
        }
    }
    
    /**
     * 发送心跳事件
     * 
     * @param uid 设备UID
     */
    public static void notifyHeartbeat(String uid) {
        // 通知UI
        EventBus.getInstance().postHeartbeat(uid);
        
        // 通知所有监听器
        for (DeviceSessionListener listener : sessionListeners) {
            listener.onHeartbeat(uid);
        }
    }
    
    /**
     * 获取设备IP地址
     * 
     * @param channel 通道
     * @return IP地址字符串
     */
    private static String getIpAddress(Channel channel) {
        if (channel.remoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
            return address.getAddress().getHostAddress();
        }
        return "unknown";
    }

    //判断是否注册
    public Channel isReg(String uid) {
        return regSession.get(uid);
    }

    public static void connect(Channel channel, String uid){
        //重复链接清除旧链接
        if(regSession.containsKey(uid)){
            Channel oldChannel = regSession.get(uid);
            channelInfo.remove(oldChannel.id());
            oldChannel.close();
            log.info("----------------offline uid------{}--------", uid);
        }
        log.info("---------------------uid:{}--------------online----", uid);
        regSession.put(uid, channel);
        channelInfo.put(channel.id(), uid);
        
        // 通知设备连接
        notifyDeviceConnected(uid, channel);
    }

    public static void disconnect(Channel channel){
        String uid = channelInfo.remove(channel.id());
        if (uid != null){
            log.info("----------------offline uid------{}--------", uid);
            regSession.remove(uid);
            
            // 通知设备断开连接
            notifyDeviceDisconnected(uid);
        }
    }

    public String subDeviceData(String uid){
        return null;
    }


    public static String getUidByChannelId(ChannelId channelId){
        return channelInfo.get(channelId);
    }

    /**
     * 响应消息缓存
     */
    public static Cache<String, BlockingQueue<String>> responseMsgCache = CacheBuilder.newBuilder()
            .maximumSize(50000)
            .expireAfterWrite(4, TimeUnit.SECONDS)
            .build();


    /**
     * 等待响应消息
     * @param key 消息唯一标识
     * @return ReceiveDdcMsgVo
     */
    public String waitReceiveMsg(String key) {
//        System.out.println("waitReceiveMsg.size()->>>>>>>>>>>>>>>>>>>" + responseMsgCache.size());
        try {
            //设置超时时间
            String vo = Objects.requireNonNull(responseMsgCache.getIfPresent(key))
                    .poll(4000, TimeUnit.MILLISECONDS);
            //删除key
            responseMsgCache.invalidate(key);
            return vo;
        } catch (Exception e) {
            log.error("Fetch data exception,sn={},msg=null",key);
            return null;
        }
    }

    /**
     * 初始化响应消息的队列
     * @param key 消息唯一标识
     */
    public void initReceiveMsg(String key) {
        responseMsgCache.put(key,new LinkedBlockingQueue<String>(1));
//        System.out.println("initReceiveMsg.size()->>>>>>>>>>>>>>>>>>>" + responseMsgCache.size());
    }

    /**
     * 设置响应消息
     * @param key 消息唯一标识
     */
    public void setReceiveMsg(String key, String msg) {
//        System.out.println("setReceiveMsg.size()->>>>>>>>>>>>>>>>>>>" + responseMsgCache.size());
        if(responseMsgCache.getIfPresent(key) != null){
            responseMsgCache.getIfPresent(key).add(msg);
            // 通知消息接收 - 从key中提取设备ID和消息类型（需要根据实际的key格式进行调整）
            String deviceId = extractDeviceIdFromKey(key);
            if (deviceId != null) {
                notifyMessageReceived(deviceId, extractMessageTypeFromKey(key), msg);
            }
            return;
        }
        log.warn("sn {} not empty",key);
    }
    
    /**
     * 从key中提取设备ID
     * 这个方法需要根据实际的key格式进行调整
     */
    private String extractDeviceIdFromKey(String key) {
        // 示例实现，假设key格式为 "typeId_channelId"
        if (key != null && key.contains("_")) {
            String channelId = key.substring(key.indexOf("_") + 1);
            return getUidByChannelId(io.netty.channel.DefaultChannelId.newInstance());
        }
        return null;
    }
    
    /**
     * 从key中提取消息类型
     * 这个方法需要根据实际的key格式进行调整
     */
    private String extractMessageTypeFromKey(String key) {
        // 示例实现，假设key格式为 "typeId_channelId"
        if (key != null && key.contains("_")) {
            return key.substring(0, key.indexOf("_"));
        }
        return "UNKNOWN";
    }
    
    /**
     * 发送消息到设备
     * 
     * @param uid 设备ID
     * @param type 消息类型
     * @param message 消息内容
     * @return 是否发送成功
     */
    public boolean sendMessage(String uid, String type, Object message) {
        Channel channel = isReg(uid);
        if (channel == null || !channel.isActive()) {
            log.warn("Device not connected: {}", uid);
            return false;
        }
        
        try {
            // 这里需要根据实际的消息发送机制进行调整
            channel.writeAndFlush(message);
            
            // 通知消息发送
            notifyMessageSent(uid, type, message.toString());
            
            return true;
        } catch (Exception e) {
            log.error("Error sending message to device {}: {}", uid, e.getMessage());
            return false;
        }
    }
}