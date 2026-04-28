package service;

import db.DBConnection;
import model.VolunteerApplication;
import model.VolunteerTeam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for volunteer policy, teams, applications, and approvals.
 */
public class VolunteerService {

    // ==================== POLICY ====================
    private static final String INSERT_POLICY_SQL =
            "INSERT INTO event_volunteer_policy (event_id, policy_type, application_mode, max_volunteers, club_id) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String GET_POLICY_SQL =
            "SELECT evp.*, c.name AS club_name FROM event_volunteer_policy evp " +
            "LEFT JOIN clubs c ON c.id = evp.club_id WHERE evp.event_id = ?";

    // ==================== TEAMS ====================
    private static final String INSERT_TEAM_SQL =
            "INSERT INTO volunteer_teams (event_id, team_name, max_members) VALUES (?, ?, ?)";

    private static final String LIST_TEAMS_SQL =
            "SELECT vt.id, vt.event_id, vt.team_name, vt.max_members, " +
            "COUNT(va.id) AS current_members " +
            "FROM volunteer_teams vt " +
            "LEFT JOIN volunteer_applications va ON va.team_id = vt.id AND va.status = 'APPROVED' " +
            "WHERE vt.event_id = ? GROUP BY vt.id ORDER BY vt.team_name";

    // ==================== APPLICATION ====================
    private static final String INSERT_APPLICATION_SQL =
            "INSERT INTO volunteer_applications (event_id, user_id, team_id, application_type, team_leader_id, status, note) " +
            "VALUES (?, ?, ?, ?, ?, 'PENDING', ?)";

    private static final String LIST_APPLICATIONS_SQL =
            "SELECT va.*, u.name AS user_name, u.email AS user_email, vt.team_name " +
            "FROM volunteer_applications va " +
            "JOIN users u ON u.id = va.user_id " +
            "LEFT JOIN volunteer_teams vt ON vt.id = va.team_id " +
            "WHERE va.event_id = ? ORDER BY va.applied_at DESC";

    private static final String MY_APPLICATIONS_SQL =
            "SELECT va.*, e.title AS event_title, vt.team_name " +
            "FROM volunteer_applications va " +
            "JOIN events e ON e.id = va.event_id " +
            "LEFT JOIN volunteer_teams vt ON vt.id = va.team_id " +
            "WHERE va.user_id = ? ORDER BY va.applied_at DESC";

    private static final String UPDATE_APPLICATION_SQL =
            "UPDATE volunteer_applications SET status = ?, reviewed_at = NOW() WHERE id = ?";

    private static final String APPROVE_TEAM_SQL =
            "UPDATE volunteer_applications SET status = ?, reviewed_at = NOW() " +
            "WHERE team_leader_id = ? AND event_id = ?";

    private static final String COUNT_APPROVED_SQL =
            "SELECT COUNT(*) AS cnt FROM volunteer_applications " +
            "WHERE event_id = ? AND status = 'APPROVED'";

    private static final String TEAM_MEMBER_COUNT_SQL =
            "SELECT COUNT(*) AS cnt FROM volunteer_applications " +
            "WHERE team_id = ? AND status = 'APPROVED'";

    // ==================== POLICY METHODS ====================

    public String createPolicy(int eventId, String policyType, String appMode,
                                int maxVolunteers, int clubId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_POLICY_SQL)) {
            ps.setInt(1, eventId);
            ps.setString(2, policyType);
            ps.setString(3, appMode);
            ps.setInt(4, maxVolunteers);
            if (clubId > 0) {
                ps.setInt(5, clubId);
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            return "Volunteer policy created for Event ID: " + eventId;
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                return "Policy already exists for this event.";
            }
            return "Failed: " + e.getMessage();
        }
    }

    public String[] getPolicy(int eventId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_POLICY_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                        rs.getString("policy_type"),
                        rs.getString("application_mode"),
                        String.valueOf(rs.getInt("max_volunteers")),
                        String.valueOf(rs.getInt("club_id")),
                        rs.getString("club_name") != null ? rs.getString("club_name") : "N/A"
                    };
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return null;
    }

    // ==================== TEAM METHODS ====================

    public String createTeam(int eventId, String teamName, int maxMembers) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_TEAM_SQL)) {
            ps.setInt(1, eventId);
            ps.setString(2, teamName);
            ps.setInt(3, maxMembers);
            ps.executeUpdate();
            return "Team '" + teamName + "' created for Event ID: " + eventId;
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                return "Team '" + teamName + "' already exists for this event.";
            }
            return "Failed: " + e.getMessage();
        }
    }

    public List<VolunteerTeam> getTeams(int eventId) {
        List<VolunteerTeam> teams = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_TEAMS_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    teams.add(new VolunteerTeam(
                        rs.getInt("id"),
                        rs.getInt("event_id"),
                        rs.getString("team_name"),
                        rs.getInt("max_members")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return teams;
    }

    // ==================== APPLICATION METHODS ====================

    /**
     * Individual application — single student applies to a team.
     */
    public String applyIndividual(int eventId, int userId, int teamId, String note) {
        System.out.println("[VolunteerService] applyIndividual: eventId=" + eventId + ", userId=" + userId + ", teamId=" + teamId);
        // Check policy eligibility
        String eligibility = checkEligibility(eventId, userId);
        if (eligibility != null) return eligibility;

        // Check max volunteers not exceeded
        if (isMaxReached(eventId)) return "Maximum volunteer slots are full for this event.";

        // Check team capacity
        if (isTeamFull(teamId)) return "This team is full.";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_APPLICATION_SQL)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            if (teamId > 0) {
                ps.setInt(3, teamId);
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, "INDIVIDUAL");
            ps.setNull(5, java.sql.Types.INTEGER);
            ps.setString(6, note);
            ps.executeUpdate();
            return "Application submitted! Waiting for admin approval.";
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                return "You have already applied for this event.";
            }
            return "Application failed: " + e.getMessage();
        }
    }

    /**
     * Team-based application — leader applies on behalf of all members (one-shot).
     * memberIds includes the leader's own ID.
     */
    public String applyTeam(int eventId, int leaderId, int teamId, String note, int[] memberIds) {
        // Check policy eligibility for leader
        String eligibility = checkEligibility(eventId, leaderId);
        if (eligibility != null) return eligibility;

        if (isMaxReached(eventId)) return "Maximum volunteer slots are full for this event.";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try {
                // Insert leader's application
                try (PreparedStatement ps = con.prepareStatement(INSERT_APPLICATION_SQL)) {
                    ps.setInt(1, eventId);
                    ps.setInt(2, leaderId);
                    ps.setInt(3, teamId);
                    ps.setString(4, "TEAM_LEADER");
                    ps.setNull(5, java.sql.Types.INTEGER);
                    ps.setString(6, note);
                    ps.executeUpdate();
                }

                // Insert each team member's application
                for (int memberId : memberIds) {
                    if (memberId == leaderId) continue; // skip leader — already inserted
                    try (PreparedStatement ps = con.prepareStatement(INSERT_APPLICATION_SQL)) {
                        ps.setInt(1, eventId);
                        ps.setInt(2, memberId);
                        ps.setInt(3, teamId);
                        ps.setString(4, "INDIVIDUAL");
                        ps.setInt(5, leaderId);
                        ps.setString(6, "Applied via team by leader ID: " + leaderId);
                        ps.executeUpdate();
                    }
                }

                con.commit();
                return "Team application submitted for " + memberIds.length + " members! Waiting for admin approval.";
            } catch (SQLException e) {
                con.rollback();
                if ("23000".equals(e.getSQLState())) {
                    return "One or more team members have already applied for this event.";
                }
                return "Team application failed: " + e.getMessage();
            }
        } catch (SQLException e) {
            return "Connection error: " + e.getMessage();
        }
    }

    /**
     * Admin approves or rejects an application.
     * For team-based apps, approving the leader auto-approves all team members.
     */
    public String reviewApplication(int applicationId, String status) {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            // First get the application details
            VolunteerApplication app = null;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM volunteer_applications WHERE id = ?")) {
                ps.setInt(1, applicationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        app = new VolunteerApplication(
                            rs.getInt("id"), rs.getInt("event_id"), rs.getInt("user_id"),
                            rs.getInt("team_id"), rs.getString("application_type"),
                            rs.getInt("team_leader_id"), rs.getString("status"),
                            rs.getString("note"), rs.getString("applied_at"),
                            rs.getString("reviewed_at")
                        );
                    }
                }
            }

            if (app == null) {
                return "Application not found.";
            }

            // Update this application
            try (PreparedStatement ps = con.prepareStatement(UPDATE_APPLICATION_SQL)) {
                ps.setString(1, status);
                ps.setInt(2, applicationId);
                ps.executeUpdate();
            }

            // If leader is approved/rejected, update all team members too
            if ("TEAM_LEADER".equals(app.getApplicationType())) {
                try (PreparedStatement ps = con.prepareStatement(APPROVE_TEAM_SQL)) {
                    ps.setString(1, status);
                    ps.setInt(2, app.getUserId());
                    ps.setInt(3, app.getEventId());
                    ps.executeUpdate();
                }
            }

            con.commit();
            return "Application " + status + " successfully!";
        } catch (SQLException e) {
            return "Review failed: " + e.getMessage();
        }
    }

    public List<VolunteerApplication> getApplications(int eventId) {
        List<VolunteerApplication> apps = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_APPLICATIONS_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    apps.add(new VolunteerApplication(
                        rs.getInt("id"), rs.getInt("event_id"), rs.getInt("user_id"),
                        rs.getInt("team_id"), rs.getString("application_type"),
                        rs.getInt("team_leader_id"), rs.getString("status"),
                        rs.getString("note"), rs.getString("applied_at"),
                        rs.getString("reviewed_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return apps;
    }

    public List<VolunteerApplication> getMyApplications(int userId) {
        List<VolunteerApplication> apps = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(MY_APPLICATIONS_SQL)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    apps.add(new VolunteerApplication(
                        rs.getInt("id"), rs.getInt("event_id"), rs.getInt("user_id"),
                        rs.getInt("team_id"), rs.getString("application_type"),
                        rs.getInt("team_leader_id"), rs.getString("status"),
                        rs.getString("note"), rs.getString("applied_at"),
                        rs.getString("reviewed_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return apps;
    }

    // ==================== HELPERS ====================

    /**
     * Checks if the student is eligible based on event volunteer policy.
     * Returns null if eligible, error message otherwise.
     */
    private String checkEligibility(int eventId, int userId) {
        String[] policy = getPolicy(eventId);
        if (policy == null) return "No volunteer policy found for Event ID: " + eventId + ". Please contact admin.";

        String policyType = policy[0];
        int clubId = Integer.parseInt(policy[3]);

        if ("CLUB_ONLY".equals(policyType) && clubId > 0) {
            ClubService cs = new ClubService();
            if (!cs.isMember(clubId, userId)) {
                return "This event is CLUB_ONLY. You must be a member of '" + policy[4] + "' to apply.";
            }
        }
        // OPEN and MIXED allow everyone
        return null;
    }

    private boolean isMaxReached(int eventId) {
        String[] policy = getPolicy(eventId);
        if (policy == null) return false;
        int max = Integer.parseInt(policy[2]);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(COUNT_APPROVED_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") >= max;
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return false;
    }

    private boolean isTeamFull(int teamId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT vt.max_members, COUNT(va.id) AS current_count " +
                     "FROM volunteer_teams vt " +
                     "LEFT JOIN volunteer_applications va ON va.team_id = vt.id AND va.status = 'APPROVED' " +
                     "WHERE vt.id = ? GROUP BY vt.id")) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("current_count") >= rs.getInt("max_members");
                }
            }
        } catch (SQLException e) {
            System.err.println("[VolunteerService] Error: " + e.getMessage());
        }
        return false;
    }
}
