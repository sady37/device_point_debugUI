package com.qinglan.example.device_point.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple event bus implementation for communication between components
 */
public class EventBus {
    
    // Singleton instance
    private static EventBus instance;
    
    // Event types
    public enum EventType {
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED,
        MESSAGE_RECEIVED,
        MESSAGE_SENT,
        HEARTBEAT
    }
    
    // Event class
    public static class Event {
        private final EventType type;
        private final Map<String, Object> data;
        
        public Event(EventType type) {
            this.type = type;
            this.data = new HashMap<>();
        }
        
        public Event addData(String key, Object value) {
            data.put(key, value);
            return this;
        }
        
        public EventType getType() {
            return type;
        }
        
        public Object getData(String key) {
            return data.get(key);
        }
        
        public String getStringData(String key) {
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }
    }
    
    // Event listener interface
    public interface EventListener {
        void onEvent(Event event);
    }
    
    // Listeners map
    private final Map<EventType, List<EventListener>> listeners = new HashMap<>();
    
    // Private constructor
    private EventBus() {
        // Initialize listeners map
        for (EventType type : EventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }
    
    /**
     * Register a listener for all event types
     */
    public void register(EventListener listener) {
        for (EventType type : EventType.values()) {
            List<EventListener> typeListeners = listeners.get(type);
            if (!typeListeners.contains(listener)) {
                typeListeners.add(listener);
            }
        }
    }
    
    /**
     * Register a listener for a specific event type
     */
    public void register(EventType type, EventListener listener) {
        List<EventListener> typeListeners = listeners.get(type);
        if (!typeListeners.contains(listener)) {
            typeListeners.add(listener);
        }
    }
    
    /**
     * Unregister a listener from all event types
     */
    public void unregister(EventListener listener) {
        for (EventType type : EventType.values()) {
            listeners.get(type).remove(listener);
        }
    }
    
    /**
     * Unregister a listener from a specific event type
     */
    public void unregister(EventType type, EventListener listener) {
        listeners.get(type).remove(listener);
    }
    
    /**
     * Post an event to all registered listeners
     */
    public void post(Event event) {
        List<EventListener> typeListeners = listeners.get(event.getType());
        for (EventListener listener : new ArrayList<>(typeListeners)) {
            listener.onEvent(event);
        }
    }
    
    /**
     * Convenience method to post a device connected event
     */
    public void postDeviceConnected(String deviceId, String ipAddress) {
        post(new Event(EventType.DEVICE_CONNECTED)
            .addData("deviceId", deviceId)
            .addData("ipAddress", ipAddress));
    }
    
    /**
     * Convenience method to post a device disconnected event
     */
    public void postDeviceDisconnected(String deviceId) {
        post(new Event(EventType.DEVICE_DISCONNECTED)
            .addData("deviceId", deviceId));
    }
    
    /**
     * Convenience method to post a message received event
     */
    public void postMessageReceived(String deviceId, String messageType, String message) {
        post(new Event(EventType.MESSAGE_RECEIVED)
            .addData("deviceId", deviceId)
            .addData("messageType", messageType)
            .addData("message", message));
    }
    
    /**
     * Convenience method to post a message sent event
     */
    public void postMessageSent(String deviceId, String messageType, String message) {
        post(new Event(EventType.MESSAGE_SENT)
            .addData("deviceId", deviceId)
            .addData("messageType", messageType)
            .addData("message", message));
    }
    
    /**
     * Convenience method to post a heartbeat event
     */
    public void postHeartbeat(String deviceId) {
        post(new Event(EventType.HEARTBEAT)
            .addData("deviceId", deviceId)
            .addData("message", "Heartbeat"));
    }
}