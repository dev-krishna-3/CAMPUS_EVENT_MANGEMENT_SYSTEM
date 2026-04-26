package service;

import java.util.LinkedList;
import java.util.Queue;
import service.EmailService; // Explicit import to help some IDE configurations

/**
 * Manages email notifications using an asynchronous queue.
 * Implementation uses a LinkedList (DSA requirement) to store pending tasks.
 */
public class NotificationManager {
    private static NotificationManager instance;
    private final Queue<EmailTask> queue;
    private final EmailService emailService;
    private final Thread workerThread;
    private volatile boolean running = true;

    private NotificationManager() {
        this.queue = new LinkedList<>(); 
        this.emailService = new EmailService();
        
        this.workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                processQueue();
            }
        }, "NotificationWorker");
        
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public static synchronized NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /**
     * Adds a notification task to the queue.
     */
    public void sendNotification(String to, String subject, String message) {
        synchronized (queue) {
            queue.add(new EmailTask(to, subject, message));
            queue.notify();
        }
    }

    private void processQueue() {
        while (running) {
            EmailTask nextTask = null;
            
            synchronized (queue) {
                while (queue.isEmpty() && running) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running) break;
                nextTask = queue.poll();
            }

            if (nextTask != null) {
                boolean success = emailService.sendEmail(
                    nextTask.to, 
                    nextTask.subject, 
                    nextTask.message
                );
                
                if (success) {
                    System.out.println("[NotificationManager] Email sent to: " + nextTask.to);
                } else {
                    System.err.println("[NotificationManager] Failed to send email to: " + nextTask.to);
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        synchronized (queue) {
            queue.notifyAll();
        }
    }

    /**
     * Private helper class to store email task data.
     */
    private static class EmailTask {
        String to;
        String subject;
        String message;

        EmailTask(String to, String subject, String message) {
            this.to = to;
            this.subject = subject;
            this.message = message;
        }
    }
}
