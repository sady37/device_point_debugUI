package com.qinglan.example.device_point;

import com.qinglan.example.device_point.server.QlIotServer;
import com.qinglan.example.device_point.ui.RadarDebugUI;
import com.qinglan.example.device_point.ui.RadarUIController;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@SpringBootApplication
public class DevicePointApplication implements CommandLineRunner {
    private static final Logger logger = Logger.getLogger(DevicePointApplication.class.getName());
    
    // 服务器端口
    private static final int DEFAULT_PORT = 1060;
    
    // 是否启动UI界面
    private boolean enableUI = false;
    
    // 服务器实例
    private QlIotServer server;
    
    // UI实例
    private RadarDebugUI debugUI;

    public static void main(String[] args) {
        SpringApplication.run(DevicePointApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 解析命令行参数
        parseArguments(args);
        
        // 创建服务器实例
        server = new QlIotServer();
        
        // 如果启用UI，先初始化UI
        if (enableUI) {
            initializeUI();
        }
        
        // 启动服务器
        CompletableFuture<Void> serverStartFuture = server.startQLServerAsync(DEFAULT_PORT);
        
        // 处理服务器启动异常
        serverStartFuture.exceptionally(ex -> {
            logger.severe("Failed to start server: " + ex.getMessage());
            return null;
        });
    }
    
    /**
     * 解析命令行参数
     * 
     * @param args 命令行参数
     */
    private void parseArguments(String[] args) {
        // 检查是否有启用UI的参数
        enableUI = Arrays.stream(args).anyMatch(arg -> 
            arg.equalsIgnoreCase("--ui") || 
            arg.equalsIgnoreCase("-u"));
            
        logger.info("UI enabled: " + enableUI);
    }
    
    /**
     * 初始化UI界面
     */
    private void initializeUI() {
        try {
            // 设置UI外观
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // 在EDT线程中创建UI
            SwingUtilities.invokeLater(() -> {
                try {
                    debugUI = new RadarDebugUI();
                    
                    // 将UI设置到服务器
                    RadarUIController controller = new RadarUIController(debugUI);
                    server.setDebugUI(debugUI);
                    
                    // 将UI控制器添加为会话监听器
                    server.addSessionListener(controller);
                    
                    // 显示UI
                    debugUI.setVisible(true);
                } catch (Exception e) {
                    logger.severe("Failed to initialize UI: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.severe("Failed to set UI look and feel: " + e.getMessage());
        }
    }
}