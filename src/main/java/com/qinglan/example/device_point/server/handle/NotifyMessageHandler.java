package com.qinglan.example.device_point.server.handle;

import com.qinglan.example.device_point.server.msg.ServerLBSInfo;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@ChannelHandler.Sharable
@Slf4j
public class NotifyMessageHandler extends SimpleChannelInboundHandler<ServerLBSInfo.NotifyMessage> {

    /**
     * 通知信息（透传）
     * Trajectory event
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerLBSInfo.NotifyMessage msg) throws Exception {
        try {
            log.info("-------------NotifyMessage:{}----------", msg.getMessage());
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

}
