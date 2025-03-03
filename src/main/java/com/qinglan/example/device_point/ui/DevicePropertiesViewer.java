package com.qinglan.example.device_point.ui;

import com.qinglan.example.device_point.server.handle.ProItemsHandler;
import com.qinglan.example.device_point.server.handle.SetPropHandler;
import com.qinglan.example.device_point.server.session.DeviceRegSession;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI component for viewing and editing device properties
 */
public class DevicePropertiesViewer extends JPanel implements EventBus.EventListener {
    private static final Logger logger = Logger.getLogger(DevicePropertiesViewer.class.getName());
    
    // UI Components
    private JTable propertiesTable;
    private DefaultTableModel propertiesTableModel;
    private JButton refreshButton;
    private JButton editButton;
    private JLabel statusLabel;
    
    // Current device ID being viewed
    private String currentDeviceId;
    
    // 记录当前正在处理的属性编辑请求
    private String pendingPropertyKey;
    private String pendingPropertyValue;
    private int pendingPropertyRow = -1;
    
    /**
     * Constructor
     */
    public DevicePropertiesViewer() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Device Properties"));
        
        // Create table model with columns
        String[] columns = {"Property", "Value", "Description"};
        propertiesTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Disable direct editing
            }
        };
        
        // Create table
        propertiesTable = new JTable(propertiesTableModel);
        propertiesTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        propertiesTable.getTableHeader().setReorderingAllowed(false);
        
        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(propertiesTable);
        add(scrollPane, BorderLayout.CENTER);
        
        // Add control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton("Refresh Properties");
        editButton = new JButton("Edit Property");
        statusLabel = new JLabel("Select a device to view properties");
        
        refreshButton.addActionListener(this::handleRefreshProperties);
        refreshButton.setEnabled(false);
        
        editButton.addActionListener(this::handleEditProperty);
        editButton.setEnabled(false);
        
        buttonPanel.add(refreshButton);
        buttonPanel.add(editButton);
        buttonPanel.add(statusLabel);
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        // 注册监听事件总线
        EventBus.getInstance().register(EventBus.EventType.MESSAGE_RECEIVED, this);
    }
    
    /**
     * 处理从EventBus接收的事件
     */
    @Override
    public void onEvent(EventBus.Event event) {
        // 只处理消息接收事件
        if (event.getType() != EventBus.EventType.MESSAGE_RECEIVED) {
            return;
        }
        
        String deviceId = event.getStringData("deviceId");
        String messageType = event.getStringData("messageType");
        String message = event.getStringData("message");
        
        // 只处理当前设备的属性设置响应消息
        if (deviceId != null && deviceId.equals(currentDeviceId) && 
            "RECV".equals(messageType) && message != null && 
            message.contains("Property Setting") && 
            pendingPropertyKey != null && pendingPropertyRow >= 0) {
            
            SwingUtilities.invokeLater(() -> {
                // 检查消息是否表示成功
                boolean success = message.contains("Successful");
                
                if (success) {
                    // 更新表格中的值
                    propertiesTableModel.setValueAt(pendingPropertyValue, pendingPropertyRow, 1);
                    statusLabel.setText("Property " + pendingPropertyKey + " updated successfully");
                } else {
                    statusLabel.setText("Failed to update property " + pendingPropertyKey);
                }
                
                // 清除待处理状态
                pendingPropertyKey = null;
                pendingPropertyValue = null;
                pendingPropertyRow = -1;
                
                // 重新启用按钮
                refreshButton.setEnabled(true);
                editButton.setEnabled(true);
            });
        }
    }
    
    /**
     * Set the current device to view
     * 
     * @param deviceId The device ID to view
     * @param autoRefresh Whether to automatically refresh properties
     */
    public void setDevice(String deviceId, boolean autoRefresh) {
        this.currentDeviceId = deviceId;
        
        refreshButton.setEnabled(deviceId != null);
        editButton.setEnabled(deviceId != null);
        
        if (deviceId != null) {
            statusLabel.setText("Viewing properties for: " + deviceId);
            
            if (autoRefresh) {
                refreshProperties();
            } else {
                // Just load cached properties without querying device
                loadCachedProperties();
            }
        } else {
            statusLabel.setText("Select a device to view properties");
            clearProperties();
        }
    }
    
    /**
     * Handle refresh button click
     */
    private void handleRefreshProperties(ActionEvent e) {
        refreshProperties();
    }
    
    /**
     * Query device and refresh properties display
     */
    public void refreshProperties() {
        if (currentDeviceId == null) {
            return;
        }
        
        // Update status
        statusLabel.setText("Querying properties from " + currentDeviceId + "...");
        
        // Disable buttons during query
        refreshButton.setEnabled(false);
        editButton.setEnabled(false);
        
        // Run query in background
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> properties = ProItemsHandler.queryDeviceProperties(currentDeviceId);
                
                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    if (properties != null) {
                        updatePropertiesTable(properties);
                        statusLabel.setText("Properties loaded for: " + currentDeviceId);
                    } else {
                        statusLabel.setText("Failed to query properties for: " + currentDeviceId);
                    }
                    
                    // Re-enable buttons
                    refreshButton.setEnabled(true);
                    editButton.setEnabled(true);
                });
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error querying properties", ex);
                
                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    
                    // Re-enable buttons
                    refreshButton.setEnabled(true);
                    editButton.setEnabled(true);
                });
            }
        });
    }
    
    /**
     * Load properties from cache without querying device
     */
    private void loadCachedProperties() {
        if (currentDeviceId == null) {
            return;
        }
        
        Map<String, String> properties = ProItemsHandler.getCachedProperties(currentDeviceId);
        updatePropertiesTable(properties);
        
        if (properties.isEmpty()) {
            statusLabel.setText("No cached properties for: " + currentDeviceId);
        } else {
            statusLabel.setText("Loaded cached properties for: " + currentDeviceId);
        }
    }
    
    /**
     * Update the properties table with new data
     * 
     * @param properties The properties map to display
     */
    private void updatePropertiesTable(Map<String, String> properties) {
        // Clear existing rows
        propertiesTableModel.setRowCount(0);
        
        // Sort properties by key for consistent display
        Map<String, String> sortedProps = new TreeMap<>(properties);
        
        // Add property descriptions based on known keys
        for (Map.Entry<String, String> entry : sortedProps.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String description = getPropertyDescription(key);
            
            propertiesTableModel.addRow(new Object[]{key, value, description});
        }
    }
    
    /**
     * Clear all properties from the table
     */
    private void clearProperties() {
        propertiesTableModel.setRowCount(0);
    }
    
    /**
     * Handle edit property button click
     */
    private void handleEditProperty(ActionEvent e) {
        int selectedRow = propertiesTable.getSelectedRow();
        if (selectedRow < 0 || currentDeviceId == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a property to edit",
                "No Property Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String key = (String) propertiesTableModel.getValueAt(selectedRow, 0);
        String currentValue = (String) propertiesTableModel.getValueAt(selectedRow, 1);
        String description = getPropertyDescription(key);
        
        // Show edit dialog
        String newValue = showEditPropertyDialog(key, currentValue, description);
        
        if (newValue != null && !newValue.equals(currentValue)) {
            // 保存当前的编辑请求
            pendingPropertyKey = key;
            pendingPropertyValue = newValue;
            pendingPropertyRow = selectedRow;
            
            // Update status
            statusLabel.setText("Setting property " + key + " on " + currentDeviceId + "...");
            
            // Disable buttons during update
            refreshButton.setEnabled(false);
            editButton.setEnabled(false);
            
            // 使用异步方式发送属性设置请求
            CompletableFuture.runAsync(() -> {
                try {
                    // 使用修改后的SetPropHandler发送属性设置请求
                    boolean sent = SetPropHandler.setProperty(currentDeviceId, key, newValue);
                    
                    if (!sent) {
                        // 如果发送失败，更新UI
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Failed to send property update request: " + key);
                            
                            // 清除待处理状态
                            pendingPropertyKey = null;
                            pendingPropertyValue = null;
                            pendingPropertyRow = -1;
                            
                            // 重新启用按钮
                            refreshButton.setEnabled(true);
                            editButton.setEnabled(true);
                        });
                    } else {
                        // 发送成功，等待设备响应(响应将通过onEvent方法处理)
                        // 启动超时计时器
                        startResponseTimeout();
                    }
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Error setting property", ex);
                    
                    // Update UI on EDT
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        
                        // 清除待处理状态
                        pendingPropertyKey = null;
                        pendingPropertyValue = null;
                        pendingPropertyRow = -1;
                        
                        // 重新启用按钮
                        refreshButton.setEnabled(true);
                        editButton.setEnabled(true);
                    });
                }
            });
        }
    }
    
    /**
     * 启动响应超时计时器
     * 如果在指定时间内没有收到设备响应，则恢复UI状态
     */
    private void startResponseTimeout() {
        Timer timer = new Timer(5000, e -> {
            // 只有在还有待处理的请求时才处理超时
            if (pendingPropertyKey != null) {
                logger.log(Level.WARNING, "Property set response timeout: " + pendingPropertyKey);
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Timeout waiting for response on property: " + pendingPropertyKey);
                    
                    // 清除待处理状态
                    pendingPropertyKey = null;
                    pendingPropertyValue = null;
                    pendingPropertyRow = -1;
                    
                    // 重新启用按钮
                    refreshButton.setEnabled(true);
                    editButton.setEnabled(true);
                });
            }
        });
        
        // 设置为只执行一次
        timer.setRepeats(false);
        timer.start();
    }
    
    /**
     * Show dialog to edit a property value
     * 
     * @param key The property key
     * @param currentValue The current property value
     * @param description Description of the property
     * @return The new value, or null if canceled
     */
    private String showEditPropertyDialog(String key, String currentValue, String description) {
        // Special handling for known properties
        if (key.equals("radar_func_ctrl")) {
            return showWorkModeDialog(currentValue);
        } else if (key.equals("radar_install_style")) {
            return showInstallStyleDialog(currentValue);
        } else {
            // Generic property editor
            JPanel panel = new JPanel(new GridLayout(0, 1));
            
            panel.add(new JLabel("Property: " + key));
            
            if (!description.isEmpty()) {
                panel.add(new JLabel("Description: " + description));
            }
            
            JTextField valueField = new JTextField(currentValue);
            panel.add(new JLabel("Value:"));
            panel.add(valueField);
            
            int result = JOptionPane.showConfirmDialog(this, panel, 
                "Edit Property", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (result == JOptionPane.OK_OPTION) {
                return valueField.getText();
            } else {
                return null;
            }
        }
    }
    
    /**
     * Show dialog to select work mode
     * 
     * @param currentValue The current value
     * @return The new value, or null if canceled
     */
    private String showWorkModeDialog(String currentValue) {
        String[] modes = {
            "3 - People Tracking",
            "7 - Fall Detection",
            "11 - Breathing/Sleep",
            "15 - Full Features (Bed Monitoring)"
        };
        
        String selected = (String) JOptionPane.showInputDialog(
            this,
            "Select Work Mode:",
            "Edit Work Mode",
            JOptionPane.QUESTION_MESSAGE,
            null,
            modes,
            getWorkModeLabel(currentValue)
        );
        
        if (selected != null) {
            // Extract the numeric value
            return selected.substring(0, selected.indexOf(' ') - 1).trim();
        } else {
            return null;
        }
    }
    
    /**
     * Show dialog to select installation style
     * 
     * @param currentValue The current value
     * @return The new value, or null if canceled
     */
    private String showInstallStyleDialog(String currentValue) {
        String[] styles = {
            "0 - Ceiling Mount",
            "1 - Side Mount"
        };
        
        String selected = (String) JOptionPane.showInputDialog(
            this,
            "Select Installation Style:",
            "Edit Installation Style",
            JOptionPane.QUESTION_MESSAGE,
            null,
            styles,
            getInstallStyleLabel(currentValue)
        );
        
        if (selected != null) {
            // Extract the numeric value
            return selected.substring(0, 1);
        } else {
            return null;
        }
    }
    
    /**
     * Get a human-readable label for work mode
     * 
     * @param value The work mode value
     * @return The label
     */
    private String getWorkModeLabel(String value) {
        if (value == null) {
            return "Unknown";
        }
        
        switch (value) {
            case "3": return "3 - People Tracking";
            case "7": return "7 - Fall Detection";
            case "11": return "11 - Breathing/Sleep";
            case "15": return "15 - Full Features (Bed Monitoring)";
            default: return value + " - Unknown Mode";
        }
    }
    
    /**
     * Get a human-readable label for installation style
     * 
     * @param value The installation style value
     * @return The label
     */
    private String getInstallStyleLabel(String value) {
        if (value == null) {
            return "Unknown";
        }
        
        switch (value) {
            case "0": return "0 - Ceiling Mount";
            case "1": return "1 - Side Mount";
            default: return value + " - Unknown Style";
        }
    }
    
    /**
     * Get a description for a property based on its key
     * 
     * @param key The property key
     * @return The description
     */
    private String getPropertyDescription(String key) {
        switch (key) {
            case "radar_func_ctrl":
                return "Work Mode (3: Tracking, 7: Fall Detection, 11: Breathing/Sleep, 15: Full Features)";
            case "radar_install_style":
                return "Installation Method (0: Ceiling Mount, 1: Side Mount)";
            case "radar_install_height":
                return "Installation Height (in decimeters, range: 15-33)";
            case "rectangle":
                return "Detection Boundary (format: x1,y1,x2,y2,x3,y3,x4,y4 in decimeters)";
            case "declare_area":
                return "Area Settings (format: area-id,area-type,x1,y1,x2,y2,x3,y3,x4,y4)";
            case "fall_param":
                return "Fall Detection Parameters (16-byte array, BASE64 encoded)";
            case "heart_breath_param":
                return "Breathing/Heart Rate Parameters (16-byte array, BASE64 encoded)";
            case "app_compile_time":
                return "Communication Firmware Compile Time";
            case "radar_compile_time":
                return "Radar Firmware Compile Time";
            case "accelera":
                return "Radar Tilt Angle (format: x:y:z:calibration-flag)";
            case "type":
                return "Device Type (TSL60G442 for Wi-Fi, TSL60G4G for 4G)";
            case "sfver":
                return "Controller Version (2.0 for HC2, 2.6 for TK2)";
            case "radarsfver":
                return "Radar Version (1.0 or 2.3)";
            case "mac":
                return "Device MAC Address";
            case "ip_port":
                return "Platform Address (format: server:port)";
            case "ssid_password":
                return "Wi-Fi Information (format: SSID:password)";
            default:
                return "";
        }
    }
    
    /**
     * 清理资源，在组件被移除前调用
     */
    public void cleanup() {
        // 取消注册事件监听
        EventBus.getInstance().unregister(this);
    }
}