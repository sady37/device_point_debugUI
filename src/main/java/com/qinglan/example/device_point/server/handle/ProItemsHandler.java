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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handler for device property management
 * - Processes property responses from devices
 * - Provides methods to query device properties
 */
@ChannelHandler.Sharable
@Slf4j
public class ProItemsHandler extends SimpleChannelInboundHandler<ServerLBSInfo.ProPertyItems> {

    // Device session for managing connections
    private DeviceRegSession deviceRegSession = SpringUtils.getBean(DeviceRegSession.class);
    
    // Counter for generating sequence numbers
    private static final AtomicInteger seqCounter = new AtomicInteger(1);
    
    // Cache of recent property values for each device
    private static final Map<String, Map<String, String>> devicePropertiesCache = new HashMap<>();

    /**
     * Handle property response messages from devices
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.ProPertyItems msg) throws Exception {
        try {
            // Get properties from the message
            List<ServerLBSInfo.ProPertyItem> propertiesList = msg.getPropertiesList();
            
            // Convert to JSON for caching
            JSONObject jsonObject = new JSONObject();
            Map<String, String> propertyMap = new HashMap<>();
            
            for (ServerLBSInfo.ProPertyItem proPertyItem : propertiesList) {
                String key = proPertyItem.getKey();
                String value = proPertyItem.getValue();
                
                jsonObject.put(key, value);
                propertyMap.put(key, value);
            }
            
            // Get device ID
            String deviceId = DeviceRegSession.getUidByChannelId(ctx.channel().id());
            
            // Cache the properties
            if (deviceId != null) {
                synchronized (devicePropertiesCache) {
                    if (!devicePropertiesCache.containsKey(deviceId)) {
                        devicePropertiesCache.put(deviceId, new HashMap<>());
                    }
                    devicePropertiesCache.get(deviceId).putAll(propertyMap);
                }
            }
            
            // Create response key
            int type = 11;
            String channelId = ctx.channel().id().asLongText();
            String key = type + channelId;
            
            // Store in session cache for waiting requests
            deviceRegSession.setReceiveMsg(key, jsonObject.toJSONString());
            
            // Log properties
            log.info("Received {} properties from device {}", propertiesList.size(), deviceId);
            
            // Notify UI of the received properties
            if (deviceId != null) {
                String propsDisplay = propertiesList.stream()
                    .map(prop -> prop.getKey() + "=" + prop.getValue())
                    .collect(Collectors.joining(", "));
                
                EventBus.getInstance().postMessageReceived(
                    deviceId, 
                    "RECV", 
                    "Device Properties: " + propsDisplay
                );
            }
            
        } catch (Exception e) {
            log.error("Error processing property response", e);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
    
    /**
     * Send a request to get device properties
     * 
     * @param channel The device channel
     * @return The sequence number of the request, or -1 if failed
     */
    public static int sendGetPropertiesRequest(Channel channel) {
        if (channel == null || !channel.isActive()) {
            log.warn("Cannot get properties, channel is null or inactive");
            return -1;
        }
        
        try {
            // Generate sequence number
            int seq = seqCounter.getAndIncrement();
            
            // Empty request - type 11 doesn't have a content payload
            ByteBuf buffer = channel.alloc().buffer();
            // type = 11 for GetDeviceProperty
            buffer.writeByte(11);
            channel.writeAndFlush(buffer);
            
            // Get device ID for logging and notifications
            String deviceId = DeviceRegSession.getUidByChannelId(channel.id());
            log.info("Sent GetDeviceProperty request: deviceId={}, seq={}", 
                     deviceId != null ? deviceId : "unknown", seq);
            
            // Notify UI
            if (deviceId != null) {
                EventBus.getInstance().postMessageSent(
                    deviceId, 
                    "SEND", 
                    "Get Device Properties (seq: " + seq + ")"
                );
            }
            
            return seq;
        } catch (Exception e) {
            log.error("Error sending GetDeviceProperty request", e);
            return -1;
        }
    }
    
    /**
     * Query all properties from a device
     * 
     * @param deviceId The device ID
     * @return Map of property keys to values, or null if failed
     */
    public static Map<String, String> queryDeviceProperties(String deviceId) {
        DeviceRegSession session = SpringUtils.getBean(DeviceRegSession.class);
        Channel channel = session.isReg(deviceId);
        
        if (channel == null) {
            log.warn("Device not connected: {}", deviceId);
            return null;
        }
        
        try {
            // Create response key and initialize
            String responseKey = 11 + channel.id().asLongText();
            session.initReceiveMsg(responseKey);
            
            // Send the request
            int seq = sendGetPropertiesRequest(channel);
            if (seq < 0) {
                return null;
            }
            
            // Wait for response
            String response = session.waitReceiveMsg(responseKey);
            if (response == null) {
                log.warn("Query properties timeout: {}", deviceId);
                return null;
            }
            
            // Parse the response
            JSONObject responseJson = JSONObject.parseObject(response);
            
            // Convert to map
            Map<String, String> properties = new HashMap<>();
            for (String key : responseJson.keySet()) {
                properties.put(key, responseJson.getString(key));
            }
            
            // Cache the properties
            synchronized (devicePropertiesCache) {
                devicePropertiesCache.put(deviceId, properties);
            }
            
            log.info("Query properties success: deviceId={}, properties={}", deviceId, properties.size());
            
            return properties;
        } catch (Exception e) {
            log.error("Error querying device properties: deviceId={}", deviceId, e);
            return null;
        }
    }
    
    /**
     * Get cached properties for a device
     * 
     * @param deviceId The device ID
     * @return Map of property keys to values, or empty map if none cached
     */
    public static Map<String, String> getCachedProperties(String deviceId) {
        synchronized (devicePropertiesCache) {
            return devicePropertiesCache.getOrDefault(deviceId, new HashMap<>());
        }
    }
    
    /**
     * Get a specific cached property
     * 
     * @param deviceId The device ID
     * @param key The property key
     * @return The property value, or null if not found
     */
    public static String getCachedProperty(String deviceId, String key) {
        synchronized (devicePropertiesCache) {
            Map<String, String> props = devicePropertiesCache.get(deviceId);
            return props != null ? props.get(key) : null;
        }
    }
    
    /**
     * Clear cached properties for a device
     * 
     * @param deviceId The device ID
     */
    public static void clearCachedProperties(String deviceId) {
        synchronized (devicePropertiesCache) {
            devicePropertiesCache.remove(deviceId);
        }
    }
}