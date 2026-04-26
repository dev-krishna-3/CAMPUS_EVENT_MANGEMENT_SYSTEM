package service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.activation.CommandMap;
import jakarta.activation.MailcapCommandMap;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Low-level service for sending emails using SMTP and Jakarta Mail.
 */
public class EmailService {
    private Properties props;
    private Session session;
    private String fromEmail;
    private boolean emailEnabled;

    public EmailService() {
        try {
            // Fix for Jakarta Mail 2.0+ classloading in some environments
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
            mc.addMailcap("text/html;; x-java-content-handler=jakarta.mail.handlers.text_html");
            mc.addMailcap("text/xml;; x-java-content-handler=jakarta.mail.handlers.text_xml");
            mc.addMailcap("text/plain;; x-java-content-handler=jakarta.mail.handlers.text_plain");
            mc.addMailcap("multipart/*;; x-java-content-handler=jakarta.mail.handlers.multipart_mixed");
            mc.addMailcap("message/rfc822;; x-java-content-handler=jakarta.mail.handlers.message_rfc822");
            CommandMap.setDefaultCommandMap(mc);

            loadConfig();
            initializeSession();
            emailEnabled = (session != null);
        } catch (Throwable t) {
            emailEnabled = false;
            session = null;
            System.err.println("[EmailService] Disabled email notifications: " + t.getMessage());
        }
    }

    private void loadConfig() {
        props = new Properties();
        try (FileInputStream fis = new FileInputStream("src/email_config.properties")) {
            props.load(fis);
            fromEmail = props.getProperty("smtp.from");
            
            // Map our properties to Jakarta Mail standard properties
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", props.getProperty("smtp.tls", "true"));
            props.put("mail.smtp.host", props.getProperty("smtp.host"));
            props.put("mail.smtp.port", props.getProperty("smtp.port"));
            
            // Add Timeouts
            props.put("mail.smtp.connectiontimeout", "5000"); // 5s
            props.put("mail.smtp.timeout", "5000");           // 5s
            props.put("mail.debug", "true"); // Show SMTP logs for diagnosis
            
        } catch (IOException e) {
            System.err.println("âŒ Critical: Failed to load email_config.properties! " + e.getMessage());
            // Fallback defaults for safety (though they might not work without real config)
            props.put("mail.smtp.auth", "true");
        }
    }

    private void initializeSession() {
        final String username = props.getProperty("smtp.user");
        final String password = props.getProperty("smtp.password");

        if (username == null || password == null) {
            session = null;
            return;
        }

        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    /**
     * Sends a simple email.
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body content
     * @return true if sent successfully, false otherwise
     */
    public boolean sendEmail(String to, String subject, String body) {
        if (!emailEnabled || session == null) {
            return false;
        }
        if (to == null || to.trim().isEmpty() || to.contains("your-email@gmail.com")) {
            System.out.println("âš ï¸ Skipping email to placeholder or null address: " + to);
            return false;
        }

        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            System.err.println("âŒ Messaging Error: Failed to send email to " + to + ". " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends an HTML email with an inline image (e.g., QR code).
     * The image is embedded using Content-ID (CID) so it displays directly in the email body.
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlBody HTML body content (must reference image with cid:contentId)
     * @param imagePath Path to the image file to embed
     * @param contentId The Content-ID to use for the image reference
     * @return true if sent successfully
     */
    public boolean sendHtmlEmailWithImage(String to, String subject, String htmlBody, String imagePath, String contentId) {
        if (!emailEnabled || session == null) {
            return false;
        }
        if (to == null || to.trim().isEmpty() || to.contains("your-email@gmail.com")) {
            System.out.println("âš ï¸ Skipping email to placeholder or null address: " + to);
            return false;
        }

        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);

            // Create multipart/related content
            jakarta.mail.internet.MimeMultipart multipart = new jakarta.mail.internet.MimeMultipart("related");

            // Part 1: HTML body
            jakarta.mail.internet.MimeBodyPart htmlPart = new jakarta.mail.internet.MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            // Part 2: Inline image
            jakarta.mail.internet.MimeBodyPart imagePart = new jakarta.mail.internet.MimeBodyPart();
            jakarta.activation.DataSource ds = new jakarta.activation.FileDataSource(imagePath);
            imagePart.setDataHandler(new jakarta.activation.DataHandler(ds));
            imagePart.setHeader("Content-ID", "<" + contentId + ">");
            imagePart.setDisposition(jakarta.mail.internet.MimeBodyPart.INLINE);
            multipart.addBodyPart(imagePart);

            message.setContent(multipart);
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            System.err.println("âŒ Messaging Error: Failed to send HTML email to " + to + ". " + e.getMessage());
            return false;
        }
    }
}
