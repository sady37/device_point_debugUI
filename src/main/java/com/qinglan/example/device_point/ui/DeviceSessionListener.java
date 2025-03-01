package com.qinglan.example.device_point.ui;

/**
 * Interface for device session events
 */
public interface DeviceSessionListener {
    
    /**
     * Called when a device connects to the server
     * 
     * @param deviceId The unique identifier of the device
     * @param ipAddress The IP address of the device
     */
    void onDeviceConnected(String deviceId, String ipAddress);
    
    /**
     * Called when a device disconnects from the server
     * 
     * @param deviceId The unique identifier of the device
     */
    void onDeviceDisconnected(String deviceId);
    
    /**
     * Called when a message is received from a device
     * 
     * @param deviceId The unique identifier of the device
     * @param messageType The type of message
     * @param message The message content
     */
    void onMessageReceived(String deviceId, String messageType, String message);
    
    /**
     * Called when a message is sent to a device
     * 
     * @param deviceId The unique identifier of the device
     * @param messageType The type of message
     * @param message The message content
     */
    void onMessageSent(String deviceId, String messageType, String message);
    
    /**
     * Called when a heartbeat is received
     * 
     * @param deviceId The unique identifier of the device
     */
    void onHeartbeat(String deviceId);
}