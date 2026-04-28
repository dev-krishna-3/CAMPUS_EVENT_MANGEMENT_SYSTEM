package service;

import db.DBConnection;
import model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditService {
    private static final String INSERT_AUDIT_SQL =
            "INSERT INTO audit_logs(actor_email, action_name, details) VALUES (?, ?, ?)";

    private static final String LIST_AUDIT_SQL =
                    "SELECT id, actor_email, action_name, details, created_at " +
                    "FROM audit_logs ORDER BY id DESC";

    public void logAction(String actorEmail, String actionName, String details) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_AUDIT_SQL)) {
            ps.setString(1, actorEmail);
            ps.setString(2, actionName);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("[ERROR] audit log failed: " + ex.getMessage());
        }
    }

    public List<AuditLog> getAuditLogs() {
        List<AuditLog> logs = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(LIST_AUDIT_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                logs.add(new AuditLog(
                        rs.getInt("id"),
                        rs.getString("actor_email"),
                        rs.getString("action_name"),
                        rs.getString("details"),
                        rs.getString("created_at")));
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] getAuditLogs failed: " + ex.getMessage());
        }
        return logs;
    }

    public void viewAuditLogs() {
        List<AuditLog> logs = getAuditLogs();
        printLogs(logs);
    }

    private void printLogs(List<AuditLog> logs) {
        if (logs.isEmpty()) {
            System.out.println("No audit logs found.");
            return;
        }

        for (AuditLog log : logs) {
            System.out.println(
                    "[" + log.getId() + "] " +
                            log.getCreatedAt() + " | " +
                            log.getActorEmail() + " | " +
                            log.getAction() + " | " +
                            log.getDetails());
        }
    }
}
