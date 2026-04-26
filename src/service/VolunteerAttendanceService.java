package service;

import db.DBConnection;
import model.VolunteerAttendance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for volunteer attendance — check-in (QR/manual) and check-out.
 * Prevents misuse of volunteering as an excuse for bunking classes.
 */
public class VolunteerAttendanceService {

    private static final String CHECKIN_SQL =
            "INSERT INTO volunteer_attendance (event_id, user_id, check_in_time, check_in_method, attendance_date) " +
            "VALUES (?, ?, NOW(), ?, CURDATE())";

    private static final String CHECKOUT_SQL =
            "UPDATE volunteer_attendance SET check_out_time = NOW() " +
            "WHERE event_id = ? AND user_id = ? AND attendance_date = CURDATE() AND check_out_time IS NULL";

    private static final String LIST_ATTENDANCE_SQL =
            "SELECT va.*, u.name AS user_name, u.email AS user_email " +
            "FROM volunteer_attendance va " +
            "JOIN users u ON u.id = va.user_id " +
            "WHERE va.event_id = ? ORDER BY va.attendance_date DESC, va.check_in_time DESC";

    private static final String LIST_ATTENDANCE_BY_DATE_SQL =
            "SELECT va.*, u.name AS user_name, u.email AS user_email " +
            "FROM volunteer_attendance va " +
            "JOIN users u ON u.id = va.user_id " +
            "WHERE va.event_id = ? AND va.attendance_date = ? ORDER BY va.check_in_time";

    private static final String USER_ATTENDANCE_STATS_SQL =
            "SELECT " +
            "COUNT(DISTINCT va.attendance_date) AS days_present, " +
            "SUM(TIMESTAMPDIFF(MINUTE, va.check_in_time, COALESCE(va.check_out_time, NOW()))) AS total_minutes " +
            "FROM volunteer_attendance va " +
            "WHERE va.event_id = ? AND va.user_id = ?";

    private static final String IS_VOLUNTEER_SQL =
            "SELECT 1 FROM volunteer_applications WHERE event_id = ? AND user_id = ? AND status = 'APPROVED'";

    // ==================== CHECK-IN ====================

    public String checkIn(int eventId, int userId, String method) {
        // Verify the user is an approved volunteer
        if (!isApprovedVolunteer(eventId, userId)) {
            return "Check-in denied: User is not an approved volunteer for this event.";
        }

        if (method == null || method.isEmpty()) method = "MANUAL";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(CHECKIN_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ps.setString(3, method);
            ps.executeUpdate();
            return "Check-in successful! (" + method + ")";
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                return "Already checked in for today.";
            }
            return "Check-in failed: " + e.getMessage();
        }
    }

    // ==================== CHECK-OUT ====================

    public String checkOut(int eventId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(CHECKOUT_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            return rows > 0 ? "Check-out successful!" : "No active check-in found for today.";
        } catch (SQLException e) {
            return "Check-out failed: " + e.getMessage();
        }
    }

    // ==================== ATTENDANCE LISTS ====================

    public List<VolunteerAttendance> getAttendance(int eventId) {
        List<VolunteerAttendance> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_ATTENDANCE_SQL)) {
            ps.setInt(1, eventId);
            list = extractAttendance(ps);
        } catch (SQLException e) {
            System.err.println("[VolunteerAttendanceService] Error: " + e.getMessage());
        }
        return list;
    }

    public List<VolunteerAttendance> getAttendanceByDate(int eventId, String date) {
        List<VolunteerAttendance> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_ATTENDANCE_BY_DATE_SQL)) {
            ps.setInt(1, eventId);
            ps.setString(2, date);
            list = extractAttendance(ps);
        } catch (SQLException e) {
            System.err.println("[VolunteerAttendanceService] Error: " + e.getMessage());
        }
        return list;
    }

    private List<VolunteerAttendance> extractAttendance(PreparedStatement ps) throws SQLException {
        List<VolunteerAttendance> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new VolunteerAttendance(
                    rs.getInt("id"), rs.getInt("event_id"), rs.getInt("user_id"),
                    rs.getString("check_in_time"), rs.getString("check_out_time"),
                    rs.getString("check_in_method"), rs.getString("attendance_date")
                ));
            }
        }
        return list;
    }

    // ==================== USER STATS ====================

    /**
     * Returns [daysPresent, totalMinutes] for a volunteer in an event.
     */
    public int[] getUserAttendanceStats(int eventId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(USER_ATTENDANCE_STATS_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{
                        rs.getInt("days_present"),
                        rs.getInt("total_minutes")
                    };
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerAttendanceService] Error: " + e.getMessage());
        }
        return new int[]{0, 0};
    }

    // ==================== HELPER ====================

    private boolean isApprovedVolunteer(int eventId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(IS_VOLUNTEER_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
