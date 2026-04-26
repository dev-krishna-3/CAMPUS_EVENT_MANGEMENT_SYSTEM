package service;

import db.DBConnection;
import model.VolunteerTask;
import model.VolunteerActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for task assignment, status updates, and activity logs.
 */
public class VolunteerTaskService {

    private static final String INSERT_TASK_SQL =
            "INSERT INTO volunteer_tasks (event_id, team_id, assigned_to, title, description, status, priority, due_date, created_by) " +
            "VALUES (?, ?, ?, ?, ?, 'TODO', ?, ?, ?)";

    private static final String LIST_TASKS_BY_EVENT_SQL =
            "SELECT vt.*, u.name AS assignee_name, tm.team_name " +
            "FROM volunteer_tasks vt " +
            "LEFT JOIN users u ON u.id = vt.assigned_to " +
            "LEFT JOIN volunteer_teams tm ON tm.id = vt.team_id " +
            "WHERE vt.event_id = ? ORDER BY vt.priority DESC, vt.due_date ASC";

    private static final String LIST_TASKS_BY_USER_SQL =
            "SELECT vt.*, u.name AS assignee_name, tm.team_name " +
            "FROM volunteer_tasks vt " +
            "LEFT JOIN users u ON u.id = vt.assigned_to " +
            "LEFT JOIN volunteer_teams tm ON tm.id = vt.team_id " +
            "WHERE (vt.assigned_to = ? OR vt.team_id IN " +
            "(SELECT team_id FROM volunteer_applications WHERE user_id = ? AND status = 'APPROVED')) " +
            "ORDER BY vt.priority DESC, vt.due_date ASC";

    private static final String UPDATE_TASK_STATUS_SQL =
            "UPDATE volunteer_tasks SET status = ? WHERE id = ?";

    private static final String INSERT_LOG_SQL =
            "INSERT INTO volunteer_activity_logs (task_id, user_id, log_text, hours_spent) VALUES (?, ?, ?, ?)";

    private static final String CAN_MODIFY_TASK_SQL =
            "SELECT 1 FROM volunteer_tasks vt " +
            "WHERE vt.id = ? AND (" +
            "vt.assigned_to = ? OR vt.team_id IN (" +
            "SELECT team_id FROM volunteer_applications WHERE user_id = ? AND status = 'APPROVED'" +
            "))";

    private static final String LIST_LOGS_SQL =
            "SELECT val.*, u.name AS user_name FROM volunteer_activity_logs val " +
            "JOIN users u ON u.id = val.user_id WHERE val.task_id = ? ORDER BY val.created_at DESC";

    private static final String TASK_PROGRESS_SQL =
            "SELECT " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS done, " +
            "SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress, " +
            "SUM(CASE WHEN status = 'TODO' THEN 1 ELSE 0 END) AS todo " +
            "FROM volunteer_tasks WHERE event_id = ?";

    // ==================== TASK CRUD ====================

    public String createTask(int eventId, int teamId, int assignedTo,
                              String title, String description,
                              String priority, String dueDate, int createdBy) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_TASK_SQL)) {
            ps.setInt(1, eventId);
            if (teamId > 0) {
                ps.setInt(2, teamId);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            if (assignedTo > 0) {
                ps.setInt(3, assignedTo);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, title);
            ps.setString(5, description);
            ps.setString(6, priority != null ? priority : "MEDIUM");
            if (dueDate != null && !dueDate.isEmpty()) {
                ps.setTimestamp(7, java.sql.Timestamp.valueOf(dueDate));
            } else {
                ps.setNull(7, java.sql.Types.TIMESTAMP);
            }
            ps.setInt(8, createdBy);
            ps.executeUpdate();
            return "Task '" + title + "' created successfully!";
        } catch (SQLException e) {
            return "Failed to create task: " + e.getMessage();
        }
    }

    public List<VolunteerTask> getTasksByEvent(int eventId) {
        List<VolunteerTask> tasks = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_TASKS_BY_EVENT_SQL)) {
            ps.setInt(1, eventId);
            tasks = extractTasks(ps);
        } catch (SQLException e) {
            System.err.println("[VolunteerTaskService] Error: " + e.getMessage());
        }
        return tasks;
    }

    public List<VolunteerTask> getTasksByUser(int userId) {
        List<VolunteerTask> tasks = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_TASKS_BY_USER_SQL)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            tasks = extractTasks(ps);
        } catch (SQLException e) {
            System.err.println("[VolunteerTaskService] Error: " + e.getMessage());
        }
        return tasks;
    }

    private List<VolunteerTask> extractTasks(PreparedStatement ps) throws SQLException {
        List<VolunteerTask> tasks = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tasks.add(new VolunteerTask(
                    rs.getInt("id"), rs.getInt("event_id"),
                    rs.getInt("team_id"), rs.getInt("assigned_to"),
                    rs.getString("title"), rs.getString("description"),
                    rs.getString("status"), rs.getString("priority"),
                    rs.getString("due_date"), rs.getInt("created_by"),
                    rs.getString("created_at"), rs.getString("updated_at")
                ));
            }
        }
        return tasks;
    }

    // ==================== STATUS UPDATE ====================

    public String updateTaskStatus(int taskId, int userId, String newStatus) {
        // Validate status
        if (!"TODO".equals(newStatus) && !"IN_PROGRESS".equals(newStatus) && !"DONE".equals(newStatus)) {
            return "Invalid status. Use: TODO, IN_PROGRESS, DONE";
        }

        if (!canModifyTask(taskId, userId)) {
            return "Access denied: You cannot modify this task.";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_TASK_STATUS_SQL)) {
            ps.setString(1, newStatus);
            ps.setInt(2, taskId);
            int rows = ps.executeUpdate();
            return rows > 0 ? "Task status updated to: " + newStatus : "Task not found.";
        } catch (SQLException e) {
            return "Failed to update: " + e.getMessage();
        }
    }

    // ==================== ACTIVITY LOGS ====================

    public String submitActivityLog(int taskId, int userId, String logText, double hoursSpent) {
        if (!canModifyTask(taskId, userId)) {
            return "Access denied: You cannot log activity for this task.";
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_LOG_SQL)) {
            ps.setInt(1, taskId);
            ps.setInt(2, userId);
            ps.setString(3, logText);
            ps.setDouble(4, hoursSpent);
            ps.executeUpdate();
            return "Activity log submitted!";
        } catch (SQLException e) {
            return "Failed to submit log: " + e.getMessage();
        }
    }

    public List<VolunteerActivityLog> getActivityLogs(int taskId) {
        List<VolunteerActivityLog> logs = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_LOGS_SQL)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new VolunteerActivityLog(
                        rs.getInt("id"), rs.getInt("task_id"), rs.getInt("user_id"),
                        rs.getString("log_text"), rs.getDouble("hours_spent"),
                        rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerTaskService] Error: " + e.getMessage());
        }
        return logs;
    }

    // ==================== PROGRESS STATS ====================

    public int[] getTaskProgress(int eventId) {
        // Returns [total, done, in_progress, todo]
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(TASK_PROGRESS_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{
                        rs.getInt("total"),
                        rs.getInt("done"),
                        rs.getInt("in_progress"),
                        rs.getInt("todo")
                    };
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerTaskService] Error: " + e.getMessage());
        }
        return new int[]{0, 0, 0, 0};
    }

    private boolean canModifyTask(int taskId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(CAN_MODIFY_TASK_SQL)) {
            ps.setInt(1, taskId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerTaskService] Permission check failed: " + e.getMessage());
            return false;
        }
    }
}
