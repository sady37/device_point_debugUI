package com.qinglan.example.device_point.ui;

import javax.swing.*;
import java.awt.*;

public class TestRadarUI extends JFrame {
    
    public TestRadarUI() {
        setTitle("Test Radar UI");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("This is a test UI for radar device debugging"), BorderLayout.CENTER);
        
        JButton button = new JButton("Test Button");
        button.addActionListener(e -> JOptionPane.showMessageDialog(this, "Button clicked!"));
        panel.add(button, BorderLayout.SOUTH);
        
        add(panel);
        setLocationRelativeTo(null);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new TestRadarUI().setVisible(true);
                System.out.println("Test UI launched successfully!");
            } catch (Exception e) {
                System.err.println("Error launching UI: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}