package com.qinglan.example.device_point.server.protocol;

import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.ui.EventBus;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;

/**
 * Debug handler for Netty pipeline
 * Intercepts all messages for logging and UI visualization
 */
@Slf4j
@Sharable  // Add the Sharable annotation so it can be reused across channels
public class DebugHandler extends ChannelDuplexHandler {

    // Message type mapping
    private static final Map<Byte, String> messageTypeMap = new HashMap<>();
    
    static {
        // Initialize message type mapping based on the protocol documentation
        messageTypeMap.put((byte) 1, "GetServerReq");
        messageTypeMap.put((byte) 2, "GetServerResponse");
        messageTypeMap.put((byte) 3, "RegisterReq");
        messageTypeMap.put((byte) 4, "RegisterResponse");
        messageTypeMap.put((byte) 5, "ObjectFallDown");
        messageTypeMap.put((byte) 7, "HeartbeatReq");
        messageTypeMap.put((byte) 8, "HeartbeatResp");
        messageTypeMap.put((byte) 9, "SetDeviceProperty");
        messageTypeMap.put((byte) 10, "SetDevicePropertyResp");
        messageTypeMap.put((byte) 11, "GetDeviceProperty");
        messageTypeMap.put((byte) 12, "GetDevicePropertyResp");
        messageTypeMap.put((byte) 13, "RealTimeTrajectory");
        messageTypeMap.put((byte) 14, "BreathingHeartRate");
        messageTypeMap.put((byte) 15, "PositionEvent");
        messageTypeMap.put((byte) 16, "PeopleCount");
        messageTypeMap.put((byte) 17, "OTAPush");
        messageTypeMap.put((byte) 18, "OTAPushResp");
        messageTypeMap.put((byte) 19, "TrajectoryStats");
        messageTypeMap.put((byte) 24, "RestartDevice");
        messageTypeMap.put((byte) 25, "RestartDeviceResp");
        messageTypeMap.put((byte) 26, "SubscribeBreathRate");
        messageTypeMap.put((byte) 27, "SubscribeBreathRateResp");
        messageTypeMap.put((byte) 28, "ServerHeartbeat");
        messageTypeMap.put((byte) 29, "ServerHeartbeatResp");
        messageTypeMap.put((byte) 35, "DebugInfo");
        messageTypeMap.put((byte) 50, "StartVoiceCall");
        messageTypeMap.put((byte) 51, "StartVoiceCallResp");
        messageTypeMap.put((byte) 52, "StopVoiceCall");
        messageTypeMap.put((byte) 53, "StopVoiceCallResp");
    }

    /**
     * Handle incoming messages
     */

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
	    if (msg instanceof ByteBuf) {
	        ByteBuf buf = (ByteBuf) msg;
	        
	        // 记录原始的读取索引，确保不改变原始buffer
	        int originalReaderIndex = buf.readerIndex();
	        
	        // 制作一个副本用于读取消息类型
	        ByteBuf copy = buf.copy();
	        
	        try {
	            // 读取消息类型（如果可用）
	            if (copy.readableBytes() > 0) {
	                byte messageType = copy.readByte();
	                
	                // 获取设备ID
	                String deviceId = DeviceRegSession.getUidByChannelId(ctx.channel().id());
	                if (deviceId == null) {
	                    // 如果设备ID不可用，使用IP地址
	                    InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
	                    deviceId = address != null ? address.getAddress().getHostAddress() : "unknown";
	                }
	                
	                // 获取消息类型名称
	                String messageTypeName = messageTypeMap.getOrDefault(messageType, "Unknown(" + messageType + ")");
	                
	                // 创建调试消息
	                String debugMessage = String.format("%s (size: %d bytes)", messageTypeName, buf.readableBytes());
	                
	                // 发送到事件总线用于UI显示
	                EventBus.getInstance().postMessageReceived(deviceId, "RECV", debugMessage);
	                
	                // 记录消息
	                log.debug("Received message: {} from device: {}", debugMessage, deviceId);
	            }
	        } finally {
	            // 释放副本以防止内存泄漏
	            copy.release();
	            
	            // 确保原始buffer的读取索引被重置
	            buf.readerIndex(originalReaderIndex);
	        }
	    }
	    
	    // 将消息传递给下一个处理器
	    ctx.fireChannelRead(msg);
	}

    /**
     * Handle exceptions
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Log the exception
        log.error("Exception in channel: {}", cause.getMessage(), cause);
        
        // Pass to next handler
        ctx.fireExceptionCaught(cause);
    }
}