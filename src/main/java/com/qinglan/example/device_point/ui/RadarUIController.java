package com.qinglan.example.device_point.ui;

import com.qinglan.example.device_point.server.QlIotServer;
import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.ui.EventBus.Event;
import com.qinglan.example.device_point.ui.EventBus.EventListener;


import java.io.File;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Controller that connects the UI with the backend services
 */
public class RadarUIController implements EventListener, DeviceSessionListener {
    
    private static final Logger logger = Logger.getLogger(RadarUIController.class.getName());
    
    // UI reference
    private final RadarDebugUI ui;
    
    // Configuration manager
    private final ConfigurationManager configManager;
    
    // Device session
    private final DeviceRegSession deviceSession;
    
    // Connected devices tracking
    private final Map<String, String> connectedDevices = new HashMap<>();
    
    /**
     * Constructor
     * 
     * @param ui The radar debug UI
     */
    public RadarUIController(RadarDebugUI ui) {
        this.ui = ui;
        this.configManager = new ConfigurationManager();
        this.deviceSession = new DeviceRegSession();
        
        // Register as a device session listener
        registerWithDeviceSession();
    }
    
    /**
     * Register with the device session to receive events
     * This is a placeholder - actual implementation will depend on how the device session is modified
     */
    private void registerWithDeviceSession() {
        // Placeholder - in real implementation, we would register with the device session
        // to receive event notifications
        
        // For now, we'll simulate some sample data
        simulateSampleData();
    }
    
    /**
     * Simulate some sample data for testing
     */
    private void simulateSampleData() {
        // Add a couple of sample devices
        //ui.addDevice("F59D3E873F5B", "Connected", "192.168.1.100");
        //ui.addDevice("F59D3E873F52", "Connected", "192.168.1.101");
        
        // Add some sample messages
        //ui.addMessage("RECV", "F59D3E873F5B", "GetServerReq: {seq: 1, uid: \"F59D3E873F5B\"}");
        //ui.addMessage("SEND", "F59D3E873F5B", "GetServerResponse: {seq: 2, result: 0, server: \"192.168.1.133\", port: 1060}");
        //ui.addMessage("RECV", "F59D3E873F5B", "RegisterReq: {seq: 3, hwver: \"1.0\", sfver: \"2.0\", uid: \"F59D3E873F5B\"}");
        //ui.addMessage("SEND", "F59D3E873F5B", "RegisterResponse: {seq: 4, result: 0}");
        //ui.addMessage("HEART", "F59D3E873F5B", "Heartbeat");
        
        // Update connected devices map
        //connectedDevices.put("F59D3E873F5B", "192.168.1.100");
        //connectedDevices.put("F59D3E873F52", "192.168.1.101");
    }
    
    /**
     * Load a configuration file
     * 
     * @param file The configuration file
     * @return True if loading was successful, false otherwise
     */
    public boolean loadConfiguration(File file) {
        return configManager.loadConfiguration(file);
    }
    
    /**
     * Send the loaded configuration to a device
     * 
     * @param deviceId The device ID to send to
     * @return True if sending was successful, false otherwise
     */
    public boolean sendConfiguration(String deviceId) {
        if (!connectedDevices.containsKey(deviceId)) {
            logger.warning("Device not connected: " + deviceId);
            return false;
        }
        
        return configManager.sendConfigurationToDevice(deviceId);
    }
    
    /**
     * Disconnect a device
     * 
     * @param deviceId The device ID to disconnect
     * @return True if disconnection was successful, false otherwise
     */
    public boolean disconnectDevice(String deviceId) {
        if (!connectedDevices.containsKey(deviceId)) {
            logger.warning("Device not connected: " + deviceId);
            return false;
        }
        
        try {
            // In a real implementation, this would use the device session to close the connection
            logger.info("Disconnecting device: " + deviceId);
            
            // For now, just notify via event bus to simulate disconnection
            EventBus.getInstance().postDeviceDisconnected(deviceId);
            
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error disconnecting device", e);
            return false;
        }
    }
    
    /**
     * Handle events from the EventBus
     * 
     * @param event The event to handle
     */
    @Override
    public void onEvent(Event event) {
        switch (event.getType()) {
            case DEVICE_CONNECTED:
                onDeviceConnected(
                    event.getStringData("deviceId"),
                    event.getStringData("ipAddress")
                );
                break;
                
            case DEVICE_DISCONNECTED:
                onDeviceDisconnected(
                    event.getStringData("deviceId")
                );
                break;
                
            case MESSAGE_RECEIVED:
                onMessageReceived(
                    event.getStringData("deviceId"),
                    event.getStringData("messageType"),
                    event.getStringData("message")
                );
                break;
                
            case MESSAGE_SENT:
                onMessageSent(
                    event.getStringData("deviceId"),
                    event.getStringData("messageType"),
                    event.getStringData("message")
                );
                break;
                
            case HEARTBEAT:
                onHeartbeat(
                    event.getStringData("deviceId")
                );
                break;
        }
    }
    
    /**
     * Handle device connection event
     */
    @Override
    public void onDeviceConnected(String deviceId, String ipAddress) {
        logger.info("Device connected: " + deviceId + " from " + ipAddress);
        
        // Update connected devices map
        connectedDevices.put(deviceId, ipAddress);
        
        // Update UI
        ui.addDevice(deviceId, "Connected", ipAddress);
        ui.addMessage("INFO", deviceId, "Device connected from " + ipAddress);
    }
    
    /**
     * Handle device disconnection event
     */
    @Override
    public void onDeviceDisconnected(String deviceId) {
        logger.info("Device disconnected: " + deviceId);
        
        // Update connected devices map
        connectedDevices.remove(deviceId);
        
        // Update UI
        ui.removeDevice(deviceId);
        ui.addMessage("INFO", deviceId, "Device disconnected");
    }
    
    /**
     * Handle message received event
     */
    @Override
    public void onMessageReceived(String deviceId, String messageType, String message) {
        logger.fine("Message received from " + deviceId + ": " + messageType + " - " + message);
        
        // Update UI
        ui.addMessage("RECV", deviceId, message);
    }
    
    /**
     * Handle message sent event
     */
    @Override
    public void onMessageSent(String deviceId, String messageType, String message) {
        logger.fine("Message sent to " + deviceId + ": " + messageType + " - " + message);
        
        // Update UI
        ui.addMessage("SEND", deviceId, message);
    }
    
    /**
     * Handle heartbeat event
     */
    @Override
    public void onHeartbeat(String deviceId) {
        logger.finest("Heartbeat from " + deviceId);
        
        // Update UI - only if tracking heartbeats
        ui.addMessage("HEART", deviceId, "Heartbeat");
    }
}