package com.qinglan.example.device_point.server;

import com.qinglan.example.device_point.server.handle.*;
import com.qinglan.example.device_point.server.protocol.ProcotolFrameDecoder;
import com.qinglan.example.device_point.server.protocol.ProtoBufCodecSharable;
import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.ui.DeviceSessionListener;
import com.qinglan.example.device_point.ui.EventBus;
import com.qinglan.example.device_point.ui.RadarDebugUI;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class QlIotServer {
    // 添加UI界面引用，便于管理
    private RadarDebugUI debugUI;
    
    // 添加设备会话
    private DeviceRegSession deviceSession;
    
    // 监听器列表
    private final List<DeviceSessionListener> sessionListeners = new ArrayList<>();
    
    // 服务器启动状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 服务器线程
    private Thread serverThread;
    
    /**
     * 构造函数，初始化设备会话
     */
    public QlIotServer() {
        this.deviceSession = new DeviceRegSession();
    }
    
    /**
     * 添加设备会话监听器
     * 
     * @param listener 监听器
     */
    public void addSessionListener(DeviceSessionListener listener) {
        sessionListeners.add(listener);
        deviceSession.addSessionListener(listener);
    }
    
    /**
     * 移除设备会话监听器
     * 
     * @param listener 监听器
     */
    public void removeSessionListener(DeviceSessionListener listener) {
        sessionListeners.remove(listener);
        deviceSession.removeSessionListener(listener);
    }
    
    /**
     * 设置调试UI
     * 
     * @param debugUI UI组件
     */
    public void setDebugUI(RadarDebugUI debugUI) {
        this.debugUI = debugUI;
    }
    
    /**
     * 异步启动服务器
     * 
     * @param inetPort 端口号
     * @return CompletableFuture<Void> 异步结果
     */
    public CompletableFuture<Void> startQLServerAsync(int inetPort) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        if (isRunning.get()) {
            future.completeExceptionally(new IllegalStateException("Server is already running"));
            return future;
        }
        
        serverThread = new Thread(() -> {
            try {
                startQLServer(inetPort);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        serverThread.setName("QlIotServer-Thread");
        serverThread.start();
        
        return future;
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        if (isRunning.get()) {
            isRunning.set(false);
            if (serverThread != null) {
                serverThread.interrupt();
            }
            log.info("Server stopping...");
        }
    }

    public void startQLServer(int inetPort){
        // 设置运行状态
        isRunning.set(true);
        
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        ProtoBufCodecSharable MESSAGE_CODEC = new ProtoBufCodecSharable();

        GetServerHandler GET_SERVER_REC = new GetServerHandler();
        RegistResponseHandler REGIST_REC = new RegistResponseHandler();
        SetPropHandler SET_PROP_REC = new SetPropHandler();
        ProItemsHandler GET_PROP_REC = new ProItemsHandler();
        CommonResHandle COMMON_REC = new CommonResHandle();
        BreathDateHandler BREATH_MESSAGE_REC = new BreathDateHandler();
        PositionDateHandler POSITION_MESSAGE_REC = new PositionDateHandler();
        PositionEventHandler POSITION_EVENT_REC = new PositionEventHandler();
        PositionStatisticHandler POSITION_STATISTIC_REC = new PositionStatisticHandler();
        FallDownHandler FALL_DOWN_REC = new FallDownHandler();
        NumberOfPeopleHandler NUMBER_PEOPLE_REC = new NumberOfPeopleHandler();
        OtaResponseHandler OTA_RESPONSE_REC = new OtaResponseHandler();
        OtaProgressHandler OTA_PROGRESS_REC = new OtaProgressHandler();
        StartVoipHandler VOIP_START_REC = new StartVoipHandler();
        StopVoipHandler VOIP_STOP_REC = new StopVoipHandler();
        NotifyMessageHandler NOTIFY_MSG_REC = new NotifyMessageHandler();
        HeartMsgHandler HEART_REC = new HeartMsgHandler();

        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.INFO);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.group(boss, worker);
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(LOGGING_HANDLER);
                    ch.pipeline().addLast(new ProcotolFrameDecoder());
                    // 用来判断是不是 读空闲时间过长，或 写空闲时间过长
                    // 5s 内如果没有收到 channel 的数据，会触发一个 IdleState#READER_IDLE 事件
                    ch.pipeline().addLast(new IdleStateHandler(60, 0, 0));
                    ch.pipeline().addLast(MESSAGE_CODEC);
                    ch.pipeline().addLast(GET_SERVER_REC);
                    ch.pipeline().addLast(REGIST_REC);
                    // ChannelDuplexHandler 可以同时作为入站和出站处理器
                    ch.pipeline().addLast(new ChannelDuplexHandler() {
                        // 用来触发特殊事件
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception{
                            IdleStateEvent event = (IdleStateEvent) evt;
                            // 触发了读空闲事件
                            if (event.state() == IdleState.READER_IDLE) {
                                log.info("No data has been read for 60 seconds");
                                ctx.channel().close();
                            }
                        }
                    });
                    ch.pipeline().addLast(HEART_REC);
                    ch.pipeline().addLast(BREATH_MESSAGE_REC);
                    ch.pipeline().addLast(POSITION_MESSAGE_REC);
                    ch.pipeline().addLast(POSITION_EVENT_REC);
                    ch.pipeline().addLast(POSITION_STATISTIC_REC);
                    ch.pipeline().addLast(FALL_DOWN_REC);
                    ch.pipeline().addLast(SET_PROP_REC);
                    ch.pipeline().addLast(GET_PROP_REC);
                    ch.pipeline().addLast(NUMBER_PEOPLE_REC);
                    ch.pipeline().addLast(NOTIFY_MSG_REC);
                    ch.pipeline().addLast(OTA_RESPONSE_REC);
                    ch.pipeline().addLast(OTA_PROGRESS_REC);
                    ch.pipeline().addLast(VOIP_START_REC);
                    ch.pipeline().addLast(VOIP_STOP_REC);
                    ch.pipeline().addLast(COMMON_REC);
                }
            });
            Channel channel = serverBootstrap.bind(inetPort).sync().channel();
            log.info("----------------start----qlServer----port:{}--------", inetPort);
            
            // 通知UI服务器启动
            notifyServerStarted(inetPort);
            
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("server error", e);
        } finally {
            // 更新服务器状态
            isRunning.set(false);
            boss.shutdownGracefully();
            worker.shutdownGracefully();
            
            // 通知UI服务器停止
            notifyServerStopped();
        }
    }
    
    /**
     * 通知服务器启动事件
     * 
     * @param port 服务器端口
     */
    private void notifyServerStarted(int port) {
        // 通过EventBus通知UI
        EventBus.getInstance().post(new EventBus.Event(EventBus.EventType.MESSAGE_RECEIVED)
            .addData("deviceId", "System")
            .addData("messageType", "INFO")
            .addData("message", "Server started on port " + port));
    }
    
    /**
     * 通知服务器停止事件
     */
    private void notifyServerStopped() {
        // 通过EventBus通知UI
        EventBus.getInstance().post(new EventBus.Event(EventBus.EventType.MESSAGE_RECEIVED)
            .addData("deviceId", "System")
            .addData("messageType", "INFO")
            .addData("message", "Server stopped"));
    }
    
    /**
     * 获取设备会话
     * 
     * @return DeviceRegSession
     */
    public DeviceRegSession getDeviceSession() {
        return deviceSession;
    }
    
    /**
     * 获取服务器运行状态
     * 
     * @return 是否在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}