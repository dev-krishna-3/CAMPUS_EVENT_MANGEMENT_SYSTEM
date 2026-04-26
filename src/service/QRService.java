package service;

import db.DBConnection;
import model.QRCode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Service responsible for QR code generation, scanning, and check-in management.
 * Uses SHA-256 hashing with a secret key for tamper-proof QR data.
 * Uses ZXing library for QR image generation.
 */
public class QRService {

    private static final String SECRET_KEY = "CAMPUS_EVENT_SECRET_2026";
    private static final String QR_DIR = "src/qrcodes/";
    private static final int QR_SIZE = 300; // pixels

    // SQL statements
    private static final String INSERT_QR_SQL =
            "INSERT INTO qr_codes (user_id, event_id, qr_data) VALUES (?, ?, ?)";
    private static final String FIND_QR_BY_DATA_SQL =
            "SELECT qc.*, u.name AS student_name, u.email AS student_email, e.title AS event_title, e.available_seats " +
            "FROM qr_codes qc " +
            "JOIN users u ON u.id = qc.user_id " +
            "JOIN events e ON e.id = qc.event_id " +
            "WHERE qc.qr_data = ?";
    private static final String MARK_USED_SQL =
            "UPDATE qr_codes SET is_used = TRUE, scanned_at = NOW() WHERE qr_data = ?";
    private static final String UPDATE_ATTENDANCE_SQL =
            "UPDATE registrations SET registration_status = 'attended' WHERE user_id = ? AND event_id = ?";
    private static final String FIND_QR_BY_USER_EVENT_SQL =
            "SELECT qr_data FROM qr_codes WHERE user_id = ? AND event_id = ?";
    private static final String FIND_USER_BY_EMAIL_SQL =
            "SELECT id, name, email FROM users WHERE email = ?";

    // ==================== 1. HASH GENERATION ====================
    /**
     * Generates a SHA-256 hash from userId + eventId + SECRET_KEY.
     * Returns a 64-character hex string.
     */
    public static String generateHash(int userId, int eventId) {
        try {
            String input = userId + "" + eventId + SECRET_KEY;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("SHA-256 hashing failed: " + e.getMessage(), e);
        }
    }

    // ==================== 2. QR IMAGE GENERATION ====================
    /**
     * Generates a QR code PNG image using ZXing and saves it to src/qrcodes/.
     * Returns the file path of the generated image.
     */
    public static String generateQRImage(String qrData, int userId, int eventId) {
        try {
            // Ensure directory exists
            File dir = new File(QR_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "qr_" + userId + "_" + eventId + ".png";
            String filePath = QR_DIR + fileName;

            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix matrix = qrWriter.encode(qrData, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            MatrixToImageWriter.writeToPath(matrix, "PNG", Path.of(filePath));

            return filePath;
        } catch (WriterException | IOException e) {
            throw new RuntimeException("QR image generation failed: " + e.getMessage(), e);
        }
    }

    // ==================== 3. GENERATE + SAVE + EMAIL ====================
    /**
     * Full pipeline: generates hash, creates QR image, saves to DB, emails student, cleans up file.
     */
    public static void generateAndSaveQR(int userId, int eventId) {
        String qrData = generateHash(userId, eventId);
        String filePath = generateQRImage(qrData, userId, eventId);

        // Save to database
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_QR_SQL)) {
            ps.setInt(1, userId);
            ps.setInt(2, eventId);
            ps.setString(3, qrData);
            ps.executeUpdate();
        } catch (SQLException e) {
            // If duplicate (user already has QR for this event), that's fine
            if (!"23000".equals(e.getSQLState())) {
                System.err.println("[QRService] DB save failed: " + e.getMessage());
            }
        }

        // Email the QR code to the student
        emailQRToStudent(userId, eventId, filePath);

        // Cleanup: delete temporary file
        try {
            new File(filePath).delete();
        } catch (Exception ignored) {}
    }

    // ==================== 4. SCAN QR ====================
    /**
     * Validates and processes a scanned QR code.
     * Returns a ScanResult with success/failure details.
     */
    public static ScanResult scanQR(String qrData) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_QR_BY_DATA_SQL)) {

            ps.setString(1, qrData);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ScanResult(false, "Invalid QR code. Not found in system.", null, null, 0);
                }

                boolean isUsed = rs.getBoolean("is_used");
                String studentName = rs.getString("student_name");
                String eventTitle = rs.getString("event_title");
                int remainingSeats = rs.getInt("available_seats");
                int userId = rs.getInt("user_id");
                int eventId = rs.getInt("event_id");

                if (isUsed) {
                    return new ScanResult(false, "QR already scanned. Entry denied.", studentName, eventTitle, remainingSeats);
                }

                // Mark QR as used
                try (PreparedStatement markPs = con.prepareStatement(MARK_USED_SQL)) {
                    markPs.setString(1, qrData);
                    markPs.executeUpdate();
                }

                // Update registration status to 'attended'
                try (PreparedStatement attPs = con.prepareStatement(UPDATE_ATTENDANCE_SQL)) {
                    attPs.setInt(1, userId);
                    attPs.setInt(2, eventId);
                    attPs.executeUpdate();
                }

                return new ScanResult(true, "Welcome " + studentName + "! Entry allowed.", studentName, eventTitle, remainingSeats);
            }
        } catch (SQLException e) {
            return new ScanResult(false, "System error during scan: " + e.getMessage(), null, null, 0);
        }
    }

    // ==================== 5. RESEND QR ====================
    /**
     * Resends the same QR code to the student (same hash, regenerated image).
     */
    public static void resendQR(int userId, int eventId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_QR_BY_USER_EVENT_SQL)) {

            ps.setInt(1, userId);
            ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No QR code found for this registration.");
                    return;
                }

                String qrData = rs.getString("qr_data");
                String filePath = generateQRImage(qrData, userId, eventId);

                // Resend email
                emailQRToStudent(userId, eventId, filePath);

                // Cleanup
                try { new File(filePath).delete(); } catch (Exception ignored) {}

                System.out.println("QR code resent successfully!");
            }
        } catch (SQLException e) {
            System.err.println("[QRService] Resend failed: " + e.getMessage());
        }
    }

    // ==================== 6. MANUAL CHECK-IN ====================
    /**
     * Manual check-in by admin using student email and event ID.
     * Marks attendance and sets QR as used.
     */
    public static String manualCheckin(String studentEmail, int eventId, Userservice us) {
        try (Connection con = DBConnection.getConnection()) {

            // Find user by email
            int userId = -1;
            String studentName = null;
            try (PreparedStatement ps = con.prepareStatement(FIND_USER_BY_EMAIL_SQL)) {
                ps.setString(1, studentEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "Student not found with email: " + studentEmail;
                    }
                    userId = rs.getInt("id");
                    studentName = rs.getString("name");
                }
            }

            // Update registration status
            try (PreparedStatement ps = con.prepareStatement(UPDATE_ATTENDANCE_SQL)) {
                ps.setInt(1, userId);
                ps.setInt(2, eventId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    return "No registration found for " + studentName + " in Event ID: " + eventId;
                }
            }

            // Mark QR as used (if exists)
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE qr_codes SET is_used = TRUE, scanned_at = NOW() WHERE user_id = ? AND event_id = ?")) {
                ps.setInt(1, userId);
                ps.setInt(2, eventId);
                ps.executeUpdate();
            }

            return "Manual check-in successful for " + studentName + " (Event ID: " + eventId + ")";

        } catch (SQLException e) {
            return "Manual check-in failed: " + e.getMessage();
        }
    }

    // ==================== HELPER: EMAIL QR TO STUDENT ====================
    private static void emailQRToStudent(int userId, int eventId, String filePath) {
        String sql = "SELECT u.name, u.email, e.title FROM users u, events e WHERE u.id = ? AND e.id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, eventId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    String eventTitle = rs.getString("title");

                    String subject = "Your QR Code for " + eventTitle;
                    String htmlBody = "<html><body>" +
                            "<h2>Hello " + name + "!</h2>" +
                            "<p>Your registration for <b>" + eventTitle + "</b> is confirmed.</p>" +
                            "<p>Please present this QR code at the venue for check-in:</p>" +
                            "<img src='cid:qrcode' alt='QR Code' width='300' height='300'/>" +
                            "<p><small>This QR code is unique to you. Do not share it.</small></p>" +
                            "</body></html>";

                    // Use the new EmailService method for HTML + inline image
                    EmailService emailService = new EmailService();
                    emailService.sendHtmlEmailWithImage(email, subject, htmlBody, filePath, "qrcode");
                }
            }
        } catch (SQLException e) {
            System.err.println("[QRService] Failed to email QR: " + e.getMessage());
        }
    }

    // ==================== INNER CLASS: ScanResult ====================
    /**
     * Represents the result of a QR code scan operation.
     */
    public static class ScanResult {
        private boolean success;
        private String message;
        private String studentName;
        private String eventTitle;
        private int remainingSeats;

        public ScanResult(boolean success, String message, String studentName, String eventTitle, int remainingSeats) {
            this.success = success;
            this.message = message;
            this.studentName = studentName;
            this.eventTitle = eventTitle;
            this.remainingSeats = remainingSeats;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getStudentName() { return studentName; }
        public String getEventTitle() { return eventTitle; }
        public int getRemainingSeats() { return remainingSeats; }
    }
}
