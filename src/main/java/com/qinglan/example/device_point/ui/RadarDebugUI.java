package com.qinglan.example.device_point.ui;

// Java Swing 相关
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// Java AWT 相关
import java.awt.*;
import java.awt.event.ActionEvent; // 处理按钮点击事件
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

// 文件操作
import java.io.File;

// 时间格式化
import java.text.SimpleDateFormat;

// 日期和时间
import java.util.Date;

// 存储设备信息
import java.util.HashMap;
import java.util.Map;

// 日志记录
import java.util.logging.Level;
import java.util.logging.Logger;

// 自定义类和包
import com.qinglan.example.device_point.server.QlIotServer;
import com.qinglan.example.device_point.server.session.DeviceRegSession;
import com.qinglan.example.device_point.ui.EventBus.Event;
import com.qinglan.example.device_point.ui.EventBus.EventListener;
/**
 * Radar Debug UI - Main window for radar device debugging
 */
public class RadarDebugUI extends JFrame {

    private static final long serialVersionUID = 1L;

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

    // Controller
    private RadarUIController controller;

    public RadarDebugUI() {
        try {
            System.out.println("Starting RadarDebugUI initialization...");

            // Initialize the controller
            this.controller = new RadarUIController(this);

            // Set up the main window
            setTitle("Radar Debug Console");
            setSize(900, 700);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 修复：改为 EXIT_ON_CLOSE
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    handleClose();
                }
            });

            // Create the main layout
            setLayout(new BorderLayout());

            // Add components
			JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			JSplitPane upperSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			
			upperSplitPane.setTopComponent(createDevicePanel());
			upperSplitPane.setBottomComponent(createMessagesPanel());
			upperSplitPane.setResizeWeight(0.2); // 设备面板占20%高度
			
			mainSplitPane.setTopComponent(upperSplitPane);
			mainSplitPane.setBottomComponent(createConfigPanel());
			mainSplitPane.setResizeWeight(0.8); // 上部面板占80%高度
			
			add(mainSplitPane, BorderLayout.CENTER);

            // Register with event bus
            EventBus.getInstance().register(controller);

            // Set visible
            setLocationRelativeTo(null);
            setVisible(true); // 确保 UI 可见

	        // 在 UI 可见后设置确切的分割位置
	        SwingUtilities.invokeLater(() -> {
	            int height = getHeight();
	            upperSplitPane.setDividerLocation((int)(height * 0.2));
	            mainSplitPane.setDividerLocation((int)(height * 0.8));
	        });

            System.out.println("RadarDebugUI initialization completed.");
        } catch (Exception e) {
            System.err.println("Error in RadarDebugUI constructor: " + e.getMessage());
            e.printStackTrace();
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

        acceptButton.addActionListener(this::handleAcceptDevice);
        disconnectButton.addActionListener(this::handleDisconnectDevice);

        buttonPanel.add(acceptButton);
        buttonPanel.add(disconnectButton);
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

        panel.add(inputPanel, BorderLayout.CENTER);
        panel.add(sendConfigButton, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Add a device to the device table
     */
    public void addDevice(String deviceId, String status, String ipAddress) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Adding device: " + deviceId);

                // Check if device already exists
                for (int i = 0; i < deviceTableModel.getRowCount(); i++) {
                    if (deviceTableModel.getValueAt(i, 0).equals(deviceId)) {
                        // Update existing row
                        deviceTableModel.setValueAt(status, i, 1);
                        deviceTableModel.setValueAt(ipAddress, i, 2);
                        return;
                    }
                }

                // Add new row
                deviceTableModel.addRow(new Object[]{deviceId, status, ipAddress});
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
                        deviceTableModel.removeRow(i);
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
	/*
	public void addMessage(String type, String deviceId, String message) {
		SwingUtilities.invokeLater(() -> {
			try {
				String logEntry = String.format("[%s] %s: %s\n", type, deviceId, message);
				JTextArea logArea = getLogAreaForType(type);
				logArea.append(logEntry);

				// Auto-scroll to the bottom
				logArea.setCaretPosition(logArea.getDocument().getLength());
			} catch (Exception e) {
				System.err.println("Error adding message: " + e.getMessage());
			}
		});
	}
	*/
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
     * Get the log area for a specific message type
     */
    private JTextArea getLogAreaForType(String type) {
        switch (type) {
            case "RECV":
                return (JTextArea) ((JScrollPane) receivedMessagesTable.getParent().getParent()).getViewport().getView();
            case "SEND":
                return (JTextArea) ((JScrollPane) sentMessagesTable.getParent().getParent()).getViewport().getView();
            case "HEART":
                return (JTextArea) ((JScrollPane) heartbeatMessagesTable.getParent().getParent()).getViewport().getView();
            default:
                return (JTextArea) ((JScrollPane) allMessagesTable.getParent().getParent()).getViewport().getView();
        }
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

        boolean sent = controller.sendConfiguration(selectedDeviceId);
        if (!sent) {
            JOptionPane.showMessageDialog(this,
                "Failed to send configuration",
                "Error", JOptionPane.ERROR_MESSAGE);
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

        boolean disconnected = controller.disconnectDevice(selectedDeviceId);
        if (!disconnected) {
            JOptionPane.showMessageDialog(this,
                "Failed to disconnect device",
                "Error", JOptionPane.ERROR_MESSAGE);
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