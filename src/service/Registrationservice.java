package service;

import db.DBConnection;

public class Registrationservice {
    private static final String CHECK_EVENT_SQL =
            "SELECT available_seats FROM events WHERE id = ? AND status = 'open' FOR UPDATE";

    private static final String CHECK_DUPLICATE_SQL =
            "SELECT 1 FROM registrations WHERE user_id = ? AND event_id = ?";

    private static final String INSERT_REGISTRATION_SQL =
            "INSERT INTO registrations(user_id, event_id, registration_status) VALUES (?, ?, 'registered')";

    private static final String DECREMENT_SEAT_SQL =
            "UPDATE events SET available_seats = available_seats - 1 WHERE id = ?";

    public boolean register(int userid, int eventid){
        try (java.sql.Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try (java.sql.PreparedStatement checkDuplicate = con.prepareStatement(CHECK_DUPLICATE_SQL)) {
                checkDuplicate.setInt(1, userid);
                checkDuplicate.setInt(2, eventid);
                try (java.sql.ResultSet rs = checkDuplicate.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("already registered!");
                        con.rollback();
                        return false;
                    }
                }
            }

            int availableSeats;
            try (java.sql.PreparedStatement checkEvent = con.prepareStatement(CHECK_EVENT_SQL)) {
                checkEvent.setInt(1, eventid);
                try (java.sql.ResultSet rs = checkEvent.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Invalid event or event not open.");
                        con.rollback();
                        return false;
                    }
                    availableSeats = rs.getInt("available_seats");
                }
            }

            if (availableSeats <= 0) {
                System.out.println("Event is full.");
                con.rollback();
                return false;
            }

            try (java.sql.PreparedStatement ins = con.prepareStatement(INSERT_REGISTRATION_SQL);
                    java.sql.PreparedStatement dec = con.prepareStatement(DECREMENT_SEAT_SQL)) {
                ins.setInt(1, userid);
                ins.setInt(2, eventid);
                ins.executeUpdate();

                dec.setInt(1, eventid);
                dec.executeUpdate();
            }

            con.commit();
            System.out.println("Registerd Successfully!");

            // QR code generation AFTER commit — separate try-catch
            try {
                QRService.generateAndSaveQR(userid, eventid);
                System.out.println("QR code generated and emailed to student.");
            } catch (Exception e) {
                // QR failed but registration succeeded — that is fine
                System.out.println("Registration successful. QR delivery will be retried.");
            }
            
            // Trigger notifications asynchronously
            sendRegistrationNotifications(userid, eventid);
            
            return true;
        } catch (java.sql.SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                System.out.println("already registered!");
                return false;
            }
            System.out.println("[ERROR] register failed: " + ex.getMessage());
            return false;
        }
    }

    private void sendRegistrationNotifications(int userId, int eventId) {
        String sql = "SELECT u.name AS student_name, u.email AS student_email, e.title AS event_title, " +
                     "admin.name AS admin_name, admin.email AS admin_email " +
                     "FROM users u " +
                     "JOIN events e ON e.id = ? " +
                     "JOIN users admin ON admin.id = e.created_by " +
                     "WHERE u.id = ?";
        
        try (java.sql.Connection con = DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String studentName = rs.getString("student_name");
                    String studentEmail = rs.getString("student_email");
                    String eventTitle = rs.getString("event_title");
                    String adminName = rs.getString("admin_name");
                    String adminEmail = rs.getString("admin_email");

                    // 1. Notify Student
                    String studentSubject = "Registration Confirmed: " + eventTitle;
                    String studentMsg = "Hello " + studentName + ",\n\n" +
                                        "You have successfully registered for the event: " + eventTitle + ".\n" +
                                        "See you there!";
                    NotificationManager.getInstance().sendNotification(studentEmail, studentSubject, studentMsg);

                    // 2. Notify Admin
                    String adminSubject = "New Registration for " + eventTitle;
                    String adminMsg = "Hello " + adminName + ",\n\n" +
                                      "A new student (" + studentName + ") has just registered for your event: " + eventTitle + ".";
                    NotificationManager.getInstance().sendNotification(adminEmail, adminSubject, adminMsg);

                    // 3. Trigger Desktop Pop-up Notification
                    NotificationService.showNotification("New Registration! 🚀", studentName + " just registered for " + eventTitle);
                }
            }
        } catch (java.sql.SQLException ex) {
            System.err.println("[ERROR] Failed to fetch notification details: " + ex.getMessage());
        }
    }
}
