package service;

import db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * Service for volunteer reports, daily summaries, and certificate eligibility.
 * Two reward tiers:
 * 🏅 Volunteer Certificate — ≥80% attendance + all tasks DONE
 * 🏆 Excellence Award — 100% attendance + all tasks DONE before deadline + team
 * 100% attendance
 */
public class VolunteerReportService {

    // Individual volunteer report
    private static final String USER_REPORT_SQL = "SELECT " +
            "va2.user_id, u.name, u.email, vt2.team_name, " +
            "(SELECT COUNT(DISTINCT attendance_date) FROM volunteer_attendance WHERE event_id = ? AND user_id = va2.user_id) AS days_present, "
            +
            "(SELECT COUNT(*) FROM volunteer_tasks WHERE event_id = ? AND (assigned_to = va2.user_id OR team_id = va2.team_id)) AS total_tasks, "
            +
            "(SELECT COUNT(*) FROM volunteer_tasks WHERE event_id = ? AND (assigned_to = va2.user_id OR team_id = va2.team_id) AND status = 'DONE') AS done_tasks, "
            +
            "(SELECT COALESCE(SUM(hours_spent), 0) FROM volunteer_activity_logs val " +
            "  JOIN volunteer_tasks vt ON vt.id = val.task_id WHERE vt.event_id = ? AND val.user_id = va2.user_id) AS total_hours "
            +
            "FROM volunteer_applications va2 " +
            "JOIN users u ON u.id = va2.user_id " +
            "JOIN volunteer_teams vt2 ON vt2.id = va2.team_id " +
            "WHERE va2.event_id = ? AND va2.user_id = ? AND va2.status = 'APPROVED'";

    // Event-wide volunteer summary
    private static final String EVENT_SUMMARY_SQL = "SELECT " +
            "COUNT(DISTINCT va.user_id) AS total_volunteers, " +
            "(SELECT COUNT(*) FROM volunteer_tasks WHERE event_id = ?) AS total_tasks, " +
            "(SELECT COUNT(*) FROM volunteer_tasks WHERE event_id = ? AND status = 'DONE') AS done_tasks, " +
            "(SELECT COUNT(DISTINCT CONCAT(user_id, '-', attendance_date)) FROM volunteer_attendance WHERE event_id = ?) AS total_checkins, "
            +
            "(SELECT COALESCE(SUM(val.hours_spent), 0) FROM volunteer_activity_logs val " +
            "  JOIN volunteer_tasks vt ON vt.id = val.task_id WHERE vt.event_id = ?) AS total_hours_logged " +
            "FROM volunteer_applications va WHERE va.event_id = ? AND va.status = 'APPROVED'";

    // Total event days (from first to last attendance)
    private static final String EVENT_DAYS_SQL = "SELECT COUNT(DISTINCT attendance_date) AS total_days FROM volunteer_attendance WHERE event_id = ?";

    // Check if all tasks done before deadline (for excellence award)
    private static final String TASKS_BEFORE_DEADLINE_SQL = "SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'DONE' AND (due_date IS NULL OR updated_at <= due_date) THEN 1 ELSE 0 END) AS on_time "
            +
            "FROM volunteer_tasks WHERE event_id = ? AND (assigned_to = ? OR team_id IN " +
            "(SELECT team_id FROM volunteer_applications WHERE user_id = ? AND event_id = ? AND status = 'APPROVED'))";

    // Team attendance percentage
    private static final String TEAM_ATTENDANCE_SQL = "SELECT " +
            "COUNT(DISTINCT va2.user_id) AS team_size, " +
            "COUNT(DISTINCT CONCAT(vatt.user_id, '-', vatt.attendance_date)) AS team_checkins " +
            "FROM volunteer_applications va2 " +
            "LEFT JOIN volunteer_attendance vatt ON vatt.user_id = va2.user_id AND vatt.event_id = va2.event_id " +
            "WHERE va2.team_id = (SELECT team_id FROM volunteer_applications WHERE user_id = ? AND event_id = ? AND status = 'APPROVED') "
            +
            "AND va2.event_id = ? AND va2.status = 'APPROVED'";

    // ==================== INDIVIDUAL REPORT ====================

    /**
     * Returns a HashMap with all report fields for a volunteer in an event.
     */
    public HashMap<String, String> getUserReport(int eventId, int userId) {
        HashMap<String, String> report = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(USER_REPORT_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, eventId);
            ps.setInt(3, eventId);
            ps.setInt(4, eventId);
            ps.setInt(5, eventId);
            ps.setInt(6, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    report.put("name", rs.getString("name"));
                    report.put("email", rs.getString("email"));
                    report.put("team", rs.getString("team_name"));
                    report.put("daysPresent", String.valueOf(rs.getInt("days_present")));
                    report.put("totalTasks", String.valueOf(rs.getInt("total_tasks")));
                    report.put("doneTasks", String.valueOf(rs.getInt("done_tasks")));
                    report.put("totalHours", String.valueOf(rs.getDouble("total_hours")));
                } else {
                    report.put("error", "Volunteer not found or not approved for this event.");
                }
            }
        } catch (SQLException e) {
            report.put("error", e.getMessage());
        }
        return report;
    }

    // ==================== EVENT SUMMARY ====================

    public HashMap<String, String> getEventSummary(int eventId) {
        HashMap<String, String> summary = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(EVENT_SUMMARY_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, eventId);
            ps.setInt(3, eventId);
            ps.setInt(4, eventId);
            ps.setInt(5, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("totalVolunteers", String.valueOf(rs.getInt("total_volunteers")));
                    summary.put("totalTasks", String.valueOf(rs.getInt("total_tasks")));
                    summary.put("doneTasks", String.valueOf(rs.getInt("done_tasks")));
                    summary.put("totalCheckins", String.valueOf(rs.getInt("total_checkins")));
                    summary.put("totalHoursLogged", String.valueOf(rs.getDouble("total_hours_logged")));
                }
            }
        } catch (SQLException e) {
            summary.put("error", e.getMessage());
        }
        return summary;
    }

    // ==================== CERTIFICATE ELIGIBILITY ====================

    /**
     * Two-tier reward system:
     * 🏅 CERTIFICATE — ≥80% attendance + all assigned tasks DONE
     * 🏆 EXCELLENCE — 100% attendance + all tasks done BEFORE deadline + team 100%
     * attendance
     * ❌ NOT_ELIGIBLE — does not meet minimum requirements
     */
    public HashMap<String, String> checkCertificateEligibility(int eventId, int userId) {
        HashMap<String, String> result = new HashMap<>();

        // Get total event days
        int totalEventDays = getTotalEventDays(eventId);
        if (totalEventDays == 0)
            totalEventDays = 1; // avoid division by zero

        // Get user attendance days
        HashMap<String, String> userReport = getUserReport(eventId, userId);
        if (userReport.containsKey("error")) {
            result.put("eligible", "false");
            result.put("reward", "NOT_ELIGIBLE");
            result.put("reason", userReport.get("error"));
            return result;
        }

        int daysPresent = Integer.parseInt(userReport.get("daysPresent"));
        int totalTasks = Integer.parseInt(userReport.get("totalTasks"));
        int doneTasks = Integer.parseInt(userReport.get("doneTasks"));
        double attendancePct = (daysPresent * 100.0) / totalEventDays;

        result.put("attendancePct", String.format("%.1f", attendancePct));
        result.put("daysPresent", String.valueOf(daysPresent));
        result.put("totalEventDays", String.valueOf(totalEventDays));
        result.put("totalTasks", String.valueOf(totalTasks));
        result.put("doneTasks", String.valueOf(doneTasks));

        boolean allTasksDone = (totalTasks > 0 && doneTasks == totalTasks) || totalTasks == 0;
        boolean meetsMinAttendance = attendancePct >= 80.0;
        boolean perfectAttendance = attendancePct >= 100.0;

        // Check Excellence criteria
        if (perfectAttendance && allTasksDone) {
            boolean beforeDeadline = areAllTasksBeforeDeadline(eventId, userId);
            boolean teamPerfect = isTeamAttendancePerfect(eventId, userId, totalEventDays);

            if (beforeDeadline && teamPerfect) {
                result.put("eligible", "true");
                result.put("reward", "EXCELLENCE_AWARD");
                result.put("reason", "100% attendance + all tasks before deadline + team 100% attendance");
                return result;
            }
        }

        // Check Certificate criteria
        if (meetsMinAttendance && allTasksDone) {
            result.put("eligible", "true");
            result.put("reward", "VOLUNTEER_CERTIFICATE");
            result.put("reason", "≥80% attendance + all assigned tasks completed");
            return result;
        }

        // Not eligible
        result.put("eligible", "false");
        result.put("reward", "NOT_ELIGIBLE");
        StringBuilder reason = new StringBuilder();
        if (!meetsMinAttendance)
            reason.append("Attendance below 80% (").append(String.format("%.1f", attendancePct)).append("%). ");
        if (!allTasksDone)
            reason.append("Pending tasks: ").append(totalTasks - doneTasks).append(". ");
        result.put("reason", reason.toString().trim());
        return result;
    }

    // ==================== HELPERS ====================

    private int getTotalEventDays(int eventId) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(EVENT_DAYS_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("total_days");
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerReportService] Error: " + e.getMessage());
        }
        return 0;
    }

    private boolean areAllTasksBeforeDeadline(int eventId, int userId) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(TASKS_BEFORE_DEADLINE_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int onTime = rs.getInt("on_time");
                    return total > 0 && onTime == total;
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerReportService] Error: " + e.getMessage());
        }
        return false;
    }

    private boolean isTeamAttendancePerfect(int eventId, int userId, int totalEventDays) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(TEAM_ATTENDANCE_SQL)) {
            ps.setInt(1, userId);
            ps.setInt(2, eventId);
            ps.setInt(3, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int teamSize = rs.getInt("team_size");
                    int teamCheckins = rs.getInt("team_checkins");
                    if (teamSize == 0 || totalEventDays == 0)
                        return false;
                    // Team needs 100% — every member present every day
                    int expectedCheckins = teamSize * totalEventDays;
                    return teamCheckins >= expectedCheckins;
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerReportService] Error: " + e.getMessage());
        }
        return false;
    }
}
