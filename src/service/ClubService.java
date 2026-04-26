package service;

import db.DBConnection;
import model.Club;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClubService {
    private static final String INSERT_CLUB_SQL =
            "INSERT INTO clubs (name, description, created_by) VALUES (?, ?, ?)";
    private static final String LIST_CLUBS_SQL =
            "SELECT id, name, description, created_by FROM clubs ORDER BY name ASC";
    private static final String ADD_MEMBER_SQL =
            "INSERT INTO club_members (club_id, user_id) VALUES (?, ?)";
    private static final String REMOVE_MEMBER_SQL =
            "DELETE FROM club_members WHERE club_id = ? AND user_id = ?";
    private static final String LIST_MEMBERS_SQL =
            "SELECT u.id, u.name, u.email FROM club_members cm " +
            "JOIN users u ON u.id = cm.user_id WHERE cm.club_id = ? ORDER BY u.name";
    private static final String IS_MEMBER_SQL =
            "SELECT 1 FROM club_members WHERE club_id = ? AND user_id = ?";

    public String createClub(String name, String description, int createdBy) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_CLUB_SQL)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setInt(3, createdBy);
            ps.executeUpdate();
            return "Club '" + name + "' created successfully!";
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                return "Club '" + name + "' already exists.";
            }
            return "Failed to create club: " + ex.getMessage();
        }
    }

    public List<Club> getAllClubs() {
        List<Club> clubs = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_CLUBS_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                clubs.add(new Club(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("created_by")));
            }
        } catch (SQLException ex) {
            System.err.println("[ClubService] Error: " + ex.getMessage());
        }
        return clubs;
    }

    public String addMember(int clubId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(ADD_MEMBER_SQL)) {
            ps.setInt(1, clubId);
            ps.setInt(2, userId);
            ps.executeUpdate();
            return "Member added to club successfully!";
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) {
                return "User is already a member of this club.";
            }
            return "Failed to add member: " + ex.getMessage();
        }
    }

    public String removeMember(int clubId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(REMOVE_MEMBER_SQL)) {
            ps.setInt(1, clubId);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            return rows > 0 ? "Member removed from club." : "Member not found in this club.";
        } catch (SQLException ex) {
            return "Failed to remove member: " + ex.getMessage();
        }
    }

    public boolean isMember(int clubId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(IS_MEMBER_SQL)) {
            ps.setInt(1, clubId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            System.err.println("[ClubService] Error checking member: " + ex.getMessage());
            return false;
        }
    }

    public void printClubMembers(int clubId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_MEMBERS_SQL)) {
            ps.setInt(1, clubId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("  [" + rs.getInt("id") + "] " + rs.getString("name")
                            + " | " + rs.getString("email"));
                }
                if (!found) {
                    System.out.println("  No members in this club.");
                }
            }
        } catch (SQLException ex) {
            System.err.println("[ClubService] Error listing members: " + ex.getMessage());
        }
    }
}
