package service;

import db.DBConnection;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Userservice {
    private static final String FIND_BY_EMAIL_SQL =
            "SELECT u.id, u.name, u.email, u.password, r.role_name " +
                    "FROM users u " +
                    "JOIN roles r ON r.id = u.role_id " +
                    "WHERE u.email = ? AND u.is_active = TRUE";

    private static final String GET_ALL_SQL =
            "SELECT u.id, u.name, u.email, u.password, r.role_name " +
                    "FROM users u " +
                    "JOIN roles r ON r.id = u.role_id " +
                    "WHERE u.is_active = TRUE " +
                    "ORDER BY u.id";

    private static final String INSERT_SQL =
            "INSERT INTO users(name, email, password, role_id) " +
                    "VALUES (?, ?, ?, (SELECT id FROM roles WHERE role_name = ?)) " +
                    "ON DUPLICATE KEY UPDATE id = id";

    private static final String UPDATE_PASSWORD_SQL =
            "UPDATE users SET password = ? WHERE email = ?";

    public boolean updatePassword(String email, String newPassword) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_PASSWORD_SQL)) {
            ps.setString(1, newPassword);
            ps.setString(2, email);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException ex) {
            System.out.println("[ERROR] updatePassword failed: " + ex.getMessage());
            return false;
        }
    }

    public void addUser(User u){
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_SQL)) {
            ps.setString(1, u.getName());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPassword());
            ps.setString(4, u.getRole());
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("[ERROR] addUser failed: " + ex.getMessage());
        }
    }

    public List<User> getAllUser(){
        List<User> users = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(GET_ALL_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getString("role_name")));
            }
            return users;
        } catch (SQLException ex) {
            System.out.println("[ERROR] getAllUser failed: " + ex.getMessage());
            return users;
        }
    }
    public User findByEmail(String email){
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(FIND_BY_EMAIL_SQL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("role_name"));
                }
            }
            return null;
        } catch (SQLException ex) {
            System.out.println("[ERROR] findByEmail failed: " + ex.getMessage());
            return null;
        }
    }
    public List<String> getAllStudentEmails() {
        List<String> emails = new ArrayList<>();
        String sql = "SELECT email FROM users u " +
                     "JOIN roles r ON r.id = u.role_id " +
                     "WHERE r.role_name = 'student' AND u.is_active = TRUE";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] getAllStudentEmails failed: " + ex.getMessage());
        }
        return emails;
    }
}
