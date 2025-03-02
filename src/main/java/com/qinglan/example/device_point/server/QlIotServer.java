package com.qinglan.example.device_point.server;

import com.qinglan.example.device_point.server.handle.*;
import com.qinglan.example.device_point.server.protocol.DebugHandler;
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
    // UI reference
    private RadarDebugUI debugUI;
    
    // Device session
    private DeviceRegSession deviceSession;
    
    // Session listeners
    private final List<DeviceSessionListener> sessionListeners = new ArrayList<>();
    
    // Server state
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Server thread
    private Thread serverThread;
    
    /**
     * Constructor
     */
    public QlIotServer() {
        this.deviceSession = new DeviceRegSession();
    }
    
    /**
     * Add session listener
     * 
     * @param listener Listener instance
     */
    public void addSessionListener(DeviceSessionListener listener) {
        sessionListeners.add(listener);
        deviceSession.addSessionListener(listener);
    }
    
    /**
     * Remove session listener
     * 
     * @param listener Listener instance
     */
    public void removeSessionListener(DeviceSessionListener listener) {
        sessionListeners.remove(listener);
        deviceSession.removeSessionListener(listener);
    }
    
    /**
     * Set debug UI
     * 
     * @param debugUI UI component
     */
    public void setDebugUI(RadarDebugUI debugUI) {
        this.debugUI = debugUI;
    }
    
    /**
     * Start server asynchronously
     * 
     * @param inetPort Port number
     * @return CompletableFuture<Void> Async result
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
     * Stop server
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

    /**
     * Start server
     * 
     * @param inetPort Port number
     */
    public void startQLServer(int inetPort){
        // Set running state
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
        
        // Create a single @Sharable debug handler instance
        DebugHandler DEBUG_HANDLER = new DebugHandler();
        
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.group(boss, worker);
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    // Add debug handler at the beginning of the pipeline - now with @Sharable annotation
                    ch.pipeline().addLast("debugHandler", DEBUG_HANDLER);
                    
                    ch.pipeline().addLast(LOGGING_HANDLER);
                    ch.pipeline().addLast(new ProcotolFrameDecoder());
                    // Idle state handler to detect inactive channels
                    ch.pipeline().addLast(new IdleStateHandler(60, 0, 0));
                    ch.pipeline().addLast(MESSAGE_CODEC);
                    ch.pipeline().addLast(GET_SERVER_REC);
                    ch.pipeline().addLast(REGIST_REC);
                    // ChannelDuplexHandler for handling idle events
                    ch.pipeline().addLast(new ChannelDuplexHandler() {
                        // Trigger special events
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception{
                            IdleStateEvent event = (IdleStateEvent) evt;
                            // Triggered when no data has been read for a while
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
            
            // Notify UI of server start
            notifyServerStarted(inetPort);
            
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Server error", e);
        } finally {
            // Update server state
            isRunning.set(false);
            boss.shutdownGracefully();
            worker.shutdownGracefully();
            
            // Notify UI of server stop
            notifyServerStopped();
        }
    }
    
    /**
     * Notify server started event
     * 
     * @param port Server port
     */
    private void notifyServerStarted(int port) {
        // Notify via EventBus
        EventBus.getInstance().post(new EventBus.Event(EventBus.EventType.MESSAGE_RECEIVED)
            .addData("deviceId", "System")
            .addData("messageType", "INFO")
            .addData("message", "Server started on port " + port));
    }
    
    /**
     * Notify server stopped event
     */
    private void notifyServerStopped() {
        // Notify via EventBus
        EventBus.getInstance().post(new EventBus.Event(EventBus.EventType.MESSAGE_RECEIVED)
            .addData("deviceId", "System")
            .addData("messageType", "INFO")
            .addData("message", "Server stopped"));
    }
    
    /**
     * Get device session
     * 
     * @return DeviceRegSession
     */
    public DeviceRegSession getDeviceSession() {
        return deviceSession;
    }
    
    /**
     * Check if server is running
     * 
     * @return boolean
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}