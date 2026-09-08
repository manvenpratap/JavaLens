package com.example;

/**
 * Notification Service component exclusive to the newly generated codebase.
 * Demonstrates automatic addition of new standalone classes during merge operations.
 */
public class NotificationService {
    @Id
    private String notificationId;
    @NotNull
    private String defaultSender;
    private boolean pushEnabled;
    private int maxRetries;

    public NotificationService() {
        this.notificationId = "NTF-DEFAULT";
        this.defaultSender = "notifications@example.com";
        this.pushEnabled = true;
        this.maxRetries = 3;
    }

    public boolean sendEmail(String recipient, String subject, String body) {
        System.out.println("Sending email from " + defaultSender + " to " + recipient + ": " + subject);
        return true;
    }

    public boolean sendPushNotification(String userId, String message) {
        if (!pushEnabled) {
            System.err.println("Push notifications are disabled for user: " + userId);
            return false;
        }
        System.out.println("Sending push notification to " + userId + ": " + message);
        return true;
    }

    public String getDefaultSender() {
        return this.defaultSender;
    }

    public boolean isPushEnabled() {
        return this.pushEnabled;
    }
}
