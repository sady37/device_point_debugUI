package com.qinglan.example.device_point.server.handle;

import com.qinglan.example.device_point.server.msg.ServerLBSInfo;
import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.ui.EventBus;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@ChannelHandler.Sharable
@Slf4j
public class HeartMsgHandler extends SimpleChannelInboundHandler<ServerLBSInfo.CommonMessage> {


    /**
     * 服务心跳
     * server heart
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
	/* 
    protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.CommonMessage msg) throws Exception {
        try {
            ServerLBSInfo.CommonMessage.Builder serverInfo = ServerLBSInfo.CommonMessage.newBuilder();
            serverInfo.setSeq(8);

            ByteBuf buffer = ctx.alloc().buffer();
            byte[] data = serverInfo.build().toByteArray();
            //type = 2
            buffer.writeByte(8);
            buffer.writeBytes(data);
            ctx.writeAndFlush(buffer);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
	*/
	protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.CommonMessage msg) throws Exception {
    try {
        // Get the device ID from the channel
        String deviceId = DeviceRegSession.getUidByChannelId(ctx.channel().id());
		if (deviceId != null) {
			DeviceRegSession.notifyHeartbeat(deviceId);
		}
        
        // Send response to device
        ServerLBSInfo.CommonMessage.Builder serverInfo = ServerLBSInfo.CommonMessage.newBuilder();
        serverInfo.setSeq(8);

        ByteBuf buffer = ctx.alloc().buffer();
        byte[] data = serverInfo.build().toByteArray();
        buffer.writeByte(8);
        buffer.writeBytes(data);
        ctx.writeAndFlush(buffer);
        
        // Notify UI of heartbeat if device is known
        if (deviceId != null) {
            // Option 1: Using EventBus directly
            EventBus.getInstance().postHeartbeat(deviceId);
            
            // Option 2: Or use the DeviceRegSession static method
            // DeviceRegSession.notifyHeartbeat(deviceId);
            
            log.debug("Heartbeat received from device: {}", deviceId);
        }
    } finally {
        ReferenceCountUtil.release(msg);
    }
}

}
