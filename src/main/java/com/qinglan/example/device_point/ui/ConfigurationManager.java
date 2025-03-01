package com.qinglan.example.device_point.ui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qinglan.example.device_point.server.session.DeviceRegSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles loading, parsing, and sending of device configurations
 */
public class ConfigurationManager {
    
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());
    
    // Device session to access device channels
    private final DeviceRegSession deviceSession;
    
    // Current loaded configuration
    private JSONObject currentConfiguration;
    
    /**
     * Constructor
     */
    public ConfigurationManager() {
        this.deviceSession = new DeviceRegSession();
        this.currentConfiguration = null;
    }
    
    /**
     * Load configuration from file
     * 
     * @param file The configuration file
     * @return True if loading successful, false otherwise
     */
    public boolean loadConfiguration(File file) {
        try {
            if (file == null || !file.exists() || file.length() == 0) {
                logger.warning("Configuration file is empty or does not exist");
                // 使用默认配置
                this.currentConfiguration = createDefaultConfiguration();
                logger.info("Using default configuration");
                return true;
            }
            
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                logger.warning("Configuration file is empty");
                this.currentConfiguration = createDefaultConfiguration();
                logger.info("Using default configuration");
                return true;
            }
            
            JSONObject config = JSON.parseObject(content);
            
            // 基本验证
            if (!validateConfiguration(config)) {
                logger.warning("Invalid configuration format");
                return false;
            }
            
            this.currentConfiguration = config;
            logger.info("Configuration loaded successfully");
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading configuration file", e);
            // 尝试使用默认配置
            this.currentConfiguration = createDefaultConfiguration();
            logger.info("Using default configuration due to error");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing configuration", e);
            // 尝试使用默认配置
            this.currentConfiguration = createDefaultConfiguration();
            logger.info("Using default configuration due to error");
            return true;
        }
    }
    
    /**
     * 创建默认配置
     * 
     * @return 默认配置对象
     */
    private JSONObject createDefaultConfiguration() {
        JSONObject config = new JSONObject();
        JSONObject properties = new JSONObject();
        
        // 添加默认属性
        properties.put("radar_func_ctrl", "15");
        properties.put("radar_install_style", "0");
        properties.put("radar_install_height", "28");
        properties.put("rectangle", "{-30,-20; 30,-20; -30,20; 30,20}");
        properties.put("declare_area", "{}");
        properties.put("fall_param", "AAAAAAAAAAAAAAAAAAAA");
        properties.put("heart_breath_param", "GFoIAQAAAAAAAAAAAA==");
        
        config.put("properties", properties);
        return config;
    }
    
    /**
     * Validate the configuration format
     * 
     * @param config The configuration to validate
     * @return True if valid, false otherwise
     */
    private boolean validateConfiguration(JSONObject config) {
        // Basic validation - check for required fields
        // This can be expanded based on specific requirements
        
        // Example validation - ensure it has some properties defined
        if (config == null || config.isEmpty()) {
            return false;
        }
        
        // Check for common properties
        if (!config.containsKey("properties")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get property value from the configuration
     * 
     * @param key The property key
     * @return The property value, or null if not found
     */
    public String getPropertyValue(String key) {
        if (currentConfiguration == null) {
            return null;
        }
        
        JSONObject properties = currentConfiguration.getJSONObject("properties");
        if (properties == null) {
            return null;
        }
        
        return properties.getString(key);
    }
    
    /**
     * Get all properties from the configuration
     * 
     * @return Map of property keys to values
     */
    public Map<String, String> getAllProperties() {
        Map<String, String> result = new HashMap<>();
        
        if (currentConfiguration == null) {
            return result;
        }
        
        JSONObject properties = currentConfiguration.getJSONObject("properties");
        if (properties == null) {
            return result;
        }
        
        for (String key : properties.keySet()) {
            result.put(key, properties.getString(key));
        }
        
        return result;
    }
    
    /**
     * Send configuration to a device
     * 
     * @param deviceId The device ID to send to
     * @return True if successful, false otherwise
     */
    public boolean sendConfigurationToDevice(String deviceId) {
        if (currentConfiguration == null) {
            logger.warning("No configuration loaded");
            return false;
        }
        
        try {
            Map<String, String> properties = getAllProperties();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                // For each property, send a set property message
                boolean success = sendPropertyToDevice(deviceId, entry.getKey(), entry.getValue());
                if (!success) {
                    logger.warning("Failed to send property: " + entry.getKey());
                    return false;
                }
                
                // Add a small delay between messages
                Thread.sleep(100);
            }
            
            logger.info("Configuration sent successfully to device: " + deviceId);
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error sending configuration", e);
            return false;
        }
    }
    
    /**
     * Send a single property to a device
     * 
     * @param deviceId The device ID
     * @param key Property key
     * @param value Property value
     * @return True if successful, false otherwise
     */
    private boolean sendPropertyToDevice(String deviceId, String key, String value) {
        // TODO: Implement actual sending logic using the device session
        // This is a placeholder - the real implementation will depend on the server code
        
        logger.info("Sending property to device " + deviceId + ": " + key + "=" + value);
        
        // Notify via event bus
        EventBus.getInstance().postMessageSent(deviceId, "SET_PROPERTY", key + "=" + value);
        
        return true;
    }
    
    /**
     * Check if a configuration is loaded
     * 
     * @return True if configuration is loaded, false otherwise
     */
    public boolean isConfigurationLoaded() {
        return currentConfiguration != null;
    }
}