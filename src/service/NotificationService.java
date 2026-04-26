package service;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;

public class NotificationService {
    
    // Displays a native Windows/Mac/Linux desktop notification popup
    public static void showNotification(String title, String message) {
        // Checking if the OS supports it
        if (!SystemTray.isSupported()) {
            System.out.println("⚠️ Desktop Notifications not supported on this device.");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // Create a generic blank icon (safer than loading a file that might not exist)
            Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            
            TrayIcon trayIcon = new TrayIcon(image, "Campus Event System");
            trayIcon.setImageAutoSize(true);
            
            tray.add(trayIcon);
            
            // Display the popup!
            trayIcon.displayMessage(title, message, MessageType.INFO);
            
            // Clean up: Remove the invisible icon from the system tray after 5 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    tray.remove(trayIcon);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }).start();
            
        } catch (AWTException e) {
            System.out.println("⚠️ Failed to show desktop notification.");
        }
    }
}
