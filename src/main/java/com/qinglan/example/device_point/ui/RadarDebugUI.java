package com.qinglan.example.device_point.ui;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import com.qinglan.example.device_point.server.handle.ProItemsHandler;

/**
 * Radar Debug UI - Main window for radar device debugging
 * with integrated DevicePropertiesViewer
 */
public class RadarDebugUI extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(RadarDebugUI.class.getName());

    // UI Components
    private JTable deviceTable;
    private DefaultTableModel deviceTableModel;
    private JTabbedPane messagesTabbedPane;
    private JTable allMessagesTable;
    private JTable receivedMessagesTable;
    private JTable sentMessagesTable;
    private JTable heartbeatMessagesTable;
    private JTextField configFileField;
    private JButton loadConfigButton;
    private JButton sendConfigButton;
    private JButton acceptButton;
    private JButton disconnectButton;
    private boolean hideHeartbeatMessages = false;
    
    // Properties viewer component
    private DevicePropertiesViewer propertiesViewer;

    // Controller
    private RadarUIController controller;

    public RadarDebugUI() {
        try {
            System.out.println("Starting RadarDebugUI initialization...");

            // Initialize the controller
            this.controller = new RadarUIController(this);

            // Set up the main window
            setTitle("Radar Debug Console");
            setSize(1100, 800);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    handleClose();
                }
            });

            // Create the main layout
            setLayout(new BorderLayout());

            // Initialize the properties viewer
            propertiesViewer = new DevicePropertiesViewer();

            // Create split panes for layout management
            JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            JSplitPane upperSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            JSplitPane lowerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            
            // Set up the upper section - devices and messages
            upperSplitPane.setTopComponent(createDevicePanel());
            upperSplitPane.setBottomComponent(createMessagesPanel());
            upperSplitPane.setResizeWeight(0.2); // Device panel gets 20% of height
            
            // Set up the lower section - config and properties
            lowerSplitPane.setLeftComponent(createConfigPanel());
            lowerSplitPane.setRightComponent(propertiesViewer);
            lowerSplitPane.setResizeWeight(0.5); // Equal space for both panels
            
            // Combine into main layout
            mainSplitPane.setTopComponent(upperSplitPane);
            mainSplitPane.setBottomComponent(lowerSplitPane);
            mainSplitPane.setResizeWeight(0.7); // Upper section gets 70% of height
            
            add(mainSplitPane, BorderLayout.CENTER);

            // Register with event bus
            EventBus.getInstance().register(controller);

            // Add device selection listener to update properties view
            setupDeviceSelectionListener();

            // Set visible
            setLocationRelativeTo(null);
            setVisible(true);

            // Adjust divider locations after UI is visible
            SwingUtilities.invokeLater(() -> {
                int height = getHeight();
                int width = getWidth();
                upperSplitPane.setDividerLocation((int)(height * 0.2));
                mainSplitPane.setDividerLocation((int)(height * 0.7));
                lowerSplitPane.setDividerLocation((int)(width * 0.5));
            });

            System.out.println("RadarDebugUI initialization completed.");
        } catch (Exception e) {
            System.err.println("Error in RadarDebugUI constructor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set up a listener for device selection to update the properties view
     */
    private void setupDeviceSelectionListener() {
        deviceTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateSelectedDeviceProperties();
                }
            }
        });
    }

    /**
     * Update the properties viewer with the selected device's properties
     */
    private void updateSelectedDeviceProperties() {
        String selectedDeviceId = getSelectedDeviceId();
        if (selectedDeviceId != null) {
            // Update the properties viewer with the selected device
            // Auto-refresh to load the latest properties
            propertiesViewer.setDevice(selectedDeviceId, true);
        } else {
            // Clear the properties viewer when no device is selected
            propertiesViewer.setDevice(null, false);
        }
    }

    /**
     * Create the device panel showing connected devices
     */
    private JPanel createDevicePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Connected Devices"));

        // Create table model with columns
        String[] columns = {"Device ID", "Status", "IP Address", "Connected Time"};
        deviceTableModel = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Create table
        deviceTable = new JTable(deviceTableModel);
        deviceTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.getTableHeader().setReorderingAllowed(false);

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(deviceTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acceptButton = new JButton("Accept New");
        disconnectButton = new JButton("Disconnect");
        JButton refreshPropsButton = new JButton("Refresh Properties");

        acceptButton.addActionListener(this::handleAcceptDevice);
        disconnectButton.addActionListener(this::handleDisconnectDevice);
        refreshPropsButton.addActionListener(e -> updateSelectedDeviceProperties());

        buttonPanel.add(acceptButton);
        buttonPanel.add(disconnectButton);
        buttonPanel.add(refreshPropsButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Create the messages panel with tabs for different message types
     */
    private JPanel createMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Messages"));

        // Create tabbed pane
        messagesTabbedPane = new JTabbedPane();

        // Create tables for each tab
        allMessagesTable = createMessageTable();
        receivedMessagesTable = createMessageTable();
        sentMessagesTable = createMessageTable();
        heartbeatMessagesTable = createMessageTable();

        // Add tables to tabs
        messagesTabbedPane.addTab("All Messages", new JScrollPane(allMessagesTable));
        messagesTabbedPane.addTab("Received", new JScrollPane(receivedMessagesTable));
        messagesTabbedPane.addTab("Sent", new JScrollPane(sentMessagesTable));
        messagesTabbedPane.addTab("Heartbeat", new JScrollPane(heartbeatMessagesTable));

        // Add tabbed pane to panel
        panel.add(messagesTabbedPane, BorderLayout.CENTER);

        // Add control buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Add heartbeat filter checkbox
        JCheckBox hideHeartbeatCheckbox = new JCheckBox("Hide Heartbeat Messages");
        hideHeartbeatCheckbox.addActionListener(e -> toggleHeartbeatMessages(hideHeartbeatCheckbox.isSelected()));
        controlPanel.add(hideHeartbeatCheckbox);

        JButton clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> clearMessageLogs());
        controlPanel.add(clearButton);

        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Create a table for displaying messages
     */
    private JTable createMessageTable() {
        String[] columns = {"Time", "Type", "Device ID", "Message"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    /**
     * Create the configuration panel
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Configuration"));

        JPanel inputPanel = new JPanel(new BorderLayout());
        configFileField = new JTextField();
        configFileField.setEditable(false);

        loadConfigButton = new JButton("Load Config");
        loadConfigButton.addActionListener(this::handleLoadConfig);

        inputPanel.add(new JLabel("Config File: "), BorderLayout.WEST);
        inputPanel.add(configFileField, BorderLayout.CENTER);
        inputPanel.add(loadConfigButton, BorderLayout.EAST);

        sendConfigButton = new JButton("Send Configuration");
        sendConfigButton.setEnabled(false);
        sendConfigButton.addActionListener(this::handleSendConfig);

        panel.add(inputPanel, BorderLayout.NORTH);
        
        // Create a panel for config options
        JPanel configOptionsPanel = new JPanel(new BorderLayout());
        configOptionsPanel.add(sendConfigButton, BorderLayout.NORTH);
        
        // Here we could add more configuration options if needed
        
        panel.add(configOptionsPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Add a device to the device table
     */
    public void addDevice(String deviceId, String status, String ipAddress) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Adding device: " + deviceId);

                // Format current time for the connected time column
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                String timeStamp = dateFormat.format(new Date());

                // Check if device already exists
                for (int i = 0; i < deviceTableModel.getRowCount(); i++) {
                    if (deviceTableModel.getValueAt(i, 0).equals(deviceId)) {
                        // Update existing row
                        deviceTableModel.setValueAt(status, i, 1);
                        deviceTableModel.setValueAt(ipAddress, i, 2);
                        return;
                    }
                }

                // Add new row with timestamp
                deviceTableModel.addRow(new Object[]{deviceId, status, ipAddress, timeStamp});
                
                // If this is the first device, select it
                if (deviceTableModel.getRowCount() == 1) {
                    deviceTable.setRowSelectionInterval(0, 0);
                    updateSelectedDeviceProperties();
                }
            } catch (Exception e) {
                System.err.println("Error adding device: " + e.getMessage());
            }
        });
    }

    /**
     * Remove a device from the device table
     */
    public void removeDevice(String deviceId) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Removing device: " + deviceId);

                for (int i = 0; i < deviceTableModel.getRowCount(); i++) {
                    if (deviceTableModel.getValueAt(i, 0).equals(deviceId)) {
                        // Check if this is the currently selected device
                        boolean wasSelected = deviceTable.isRowSelected(i);
                        
                        // Remove the device
                        deviceTableModel.removeRow(i);
                        
                        // Clear properties viewer if the selected device was removed
                        if (wasSelected) {
                            propertiesViewer.setDevice(null, false);
                            
                            // If there are other devices, select the first one
                            if (deviceTableModel.getRowCount() > 0) {
                                deviceTable.setRowSelectionInterval(0, 0);
                                updateSelectedDeviceProperties();
                            }
                        }
                        
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error removing device: " + e.getMessage());
            }
        });
    }

    /**
     * Add a message to the logs
     */
    public void addMessage(String type, String deviceId, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Get current time
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                String timeStamp = dateFormat.format(new Date());
                
                // Add to all messages table
                DefaultTableModel allModel = (DefaultTableModel) allMessagesTable.getModel();
                allModel.addRow(new Object[]{timeStamp, type, deviceId, message});
                
                // Add to specific message type table
                JTable targetTable;
                switch (type) {
                    case "RECV":
                        targetTable = receivedMessagesTable;
                        break;
                    case "SEND":
                        targetTable = sentMessagesTable;
                        break;
                    case "HEART":
                        targetTable = heartbeatMessagesTable;
                        break;
                    default:
                        targetTable = allMessagesTable;
                        break;
                }
                
                // Add message to the appropriate table
                DefaultTableModel targetModel = (DefaultTableModel) targetTable.getModel();
                targetModel.addRow(new Object[]{timeStamp, type, deviceId, message});
                
                // Scroll to bottom of the table
                scrollToBottom(targetTable);
                scrollToBottom(allMessagesTable);

                // Apply filtering if needed
                if (hideHeartbeatMessages) {
                    filterMessages(allMessagesTable);
                    if ("RECV".equals(type)) {
                        filterMessages(receivedMessagesTable);
                    } else if ("SEND".equals(type)) {
                        filterMessages(sentMessagesTable);
                    } else if ("HEART".equals(type)) {
                        filterMessages(heartbeatMessagesTable);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Error adding message: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Scroll the table to show the latest row
     */
    private void scrollToBottom(JTable table) {
        SwingUtilities.invokeLater(() -> {
            if (table.getRowCount() > 0) {
                int lastRow = table.getRowCount() - 1;
                table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
            }
        });
    }

    /**
     * Clear all message logs
     */
    private void clearMessageLogs() {
        SwingUtilities.invokeLater(() -> {
            clearTable(allMessagesTable);
            clearTable(receivedMessagesTable);
            clearTable(sentMessagesTable);
            clearTable(heartbeatMessagesTable);
        });
    }

    /**
     * Toggle whether to show or hide heartbeat messages
     * 
     * @param hideHeartbeat whether to hide heartbeat messages
     */
    private void toggleHeartbeatMessages(boolean hideHeartbeat) {
        // Save the current selection
        this.hideHeartbeatMessages = hideHeartbeat;
        
        // Refilter all message tables
        filterMessages(allMessagesTable);
        filterMessages(receivedMessagesTable);
        filterMessages(sentMessagesTable);
    }

    /**
     * Filter messages in a table based on hideHeartbeatMessages setting
     * 
     * @param table the table to filter
     */
    private void filterMessages(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        javax.swing.table.TableRowSorter<DefaultTableModel> sorter = new javax.swing.table.TableRowSorter<>(model);
        table.setRowSorter(sorter);
        
        if (hideHeartbeatMessages) {
            // Create filter to exclude rows with "HEART" type
            javax.swing.RowFilter<DefaultTableModel, Integer> filter = new javax.swing.RowFilter<DefaultTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    String type = (String) entry.getValue(1); // Type column index is 1
                    return !"HEART".equals(type);
                }
            };
            sorter.setRowFilter(filter);
        } else {
            // Clear filter to show all rows
            sorter.setRowFilter(null);
        }
    }

    /**
     * Clear a specific table
     */
    private void clearTable(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
    }

    /**
     * Handle loading configuration file
     */
    private void handleLoadConfig(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Configuration File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // Set filter for JSON files
        javax.swing.filechooser.FileFilter jsonFilter = new javax.swing.filechooser.FileNameExtensionFilter(
            "JSON Files (*.json)", "json");
        fileChooser.addChoosableFileFilter(jsonFilter);
        fileChooser.setFileFilter(jsonFilter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            configFileField.setText(selectedFile.getAbsolutePath());

            // Load the configuration
            boolean loaded = controller.loadConfiguration(selectedFile);
            sendConfigButton.setEnabled(loaded);

            if (!loaded) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load configuration file",
                    "Error", JOptionPane.ERROR_MESSAGE);
            } else {
                // Add log message
                addMessage("INFO", "System", "Configuration loaded: " + selectedFile.getName());
            }
        }
    }

    /**
     * Handle sending configuration
     */
    private void handleSendConfig(ActionEvent e) {
        String selectedDeviceId = getSelectedDeviceId();
        if (selectedDeviceId == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a device first",
                "No Device Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirm sending configuration
        int confirm = JOptionPane.showConfirmDialog(this,
            "Send configuration to device " + selectedDeviceId + "?",
            "Confirm Configuration", JOptionPane.YES_NO_OPTION);
            
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Add log message
        addMessage("INFO", "System", "Sending configuration to " + selectedDeviceId + "...");
        
        boolean sent = controller.sendConfiguration(selectedDeviceId);
        if (!sent) {
            JOptionPane.showMessageDialog(this,
                "Failed to send configuration to " + selectedDeviceId,
                "Error", JOptionPane.ERROR_MESSAGE);
            addMessage("ERROR", "System", "Failed to send configuration to " + selectedDeviceId);
        } else {
            addMessage("INFO", "System", "Configuration sent successfully to " + selectedDeviceId);
            
            // Refresh properties after configuration is sent
            SwingUtilities.invokeLater(() -> {
                try {
                    // Give some time for the device to process the configuration
                    Thread.sleep(500);
                    updateSelectedDeviceProperties();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * Handle accepting a new device
     */
    private void handleAcceptDevice(ActionEvent e) {
        JOptionPane.showMessageDialog(this,
            "The server automatically accepts new devices when they connect.\n" +
            "See the device list for active connections.",
            "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Handle disconnecting a device
     */
    private void handleDisconnectDevice(ActionEvent e) {
        String selectedDeviceId = getSelectedDeviceId();
        if (selectedDeviceId == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a device to disconnect",
                "No Device Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirm disconnection
        int confirm = JOptionPane.showConfirmDialog(this,
            "Disconnect device " + selectedDeviceId + "?",
            "Confirm Disconnection", JOptionPane.YES_NO_OPTION);
            
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Add log message
        addMessage("INFO", "System", "Disconnecting " + selectedDeviceId + "...");
        
        boolean disconnected = controller.disconnectDevice(selectedDeviceId);
        if (!disconnected) {
            JOptionPane.showMessageDialog(this,
                "Failed to disconnect device " + selectedDeviceId,
                "Error", JOptionPane.ERROR_MESSAGE);
            addMessage("ERROR", "System", "Failed to disconnect " + selectedDeviceId);
        }
    }

    /**
     * Get the ID of the currently selected device
     */
    private String getSelectedDeviceId() {
        int selectedRow = deviceTable.getSelectedRow();
        if (selectedRow == -1) {
            return null;
        }

        return (String) deviceTable.getValueAt(selectedRow, 0);
    }

    /**
     * Handle closing the window
     */
    private void handleClose() {
        int response = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to close the debug UI?",
            "Confirm Close", JOptionPane.YES_NO_OPTION);

        if (response == JOptionPane.YES_OPTION) {
            // Unregister from event bus
            EventBus.getInstance().unregister(controller);

            // Clean up any cached properties
            ProItemsHandler.clearCachedProperties(null); // Clear all

            // Dispose the window
            dispose();
        }
    }

    /**
     * Main method to start the UI
     */
    public static void main(String[] args) {
        // Set look and feel to system default
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Start UI on EDT
        SwingUtilities.invokeLater(() -> new RadarDebugUI());
    }
}