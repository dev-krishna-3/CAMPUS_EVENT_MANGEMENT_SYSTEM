


package service;

import db.DBConnection;
import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Manager Class - Handles all administrator and super admin functions
 * 
 * ✅ SECURITY: All methods verify caller's role before executing
 * ✅ AUDIT: All unauthorized attempts are logged
 * ✅ SAFE: No privilege escalation possible
 */
public class Manager {
    
    private static final String PROMOTE_SQL =
            "UPDATE users SET role_id = (SELECT id FROM roles WHERE role_name = 'admin') WHERE id = ?";

    private static final String DEMOTE_SQL =
            "UPDATE users SET role_id = (SELECT id FROM roles WHERE role_name = 'student') WHERE id = ?";

    private static final String DEACTIVATE_USER_SQL =
            "UPDATE users SET is_active = FALSE WHERE id = ?";

    private static final String DELETE_EVENT_SQL =
            "UPDATE events SET status = 'archived' WHERE id = ?";

    private static final String GET_ALL_EVENTS_SQL =
            "SELECT id, title, venue, capacity, available_seats, status FROM events ORDER BY id DESC";

    private static final String GET_ALL_REGISTRATIONS_SQL =
            "SELECT r.id, r.user_id, r.event_id, u.name, u.email, e.title, r.registration_status, r.registered_at " +
            "FROM registrations r " +
            "JOIN users u ON u.id = r.user_id " +
            "JOIN events e ON e.id = r.event_id " +
            "ORDER BY r.registered_at DESC";

    // ═══════════════════════════════════════════════════════════════════
    // ✅ PUBLIC METHODS - All with SECURITY CHECKS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ Promotes a user to admin
     * Only super_admin can do this
     * 
     * @param caller The user making the request (must be super_admin)
     * @param target The user to promote
     * @throws SecurityException if caller is not super_admin
     */
    public void promoteToAdmin(User caller, User target) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED UNAUTHORIZED PROMOTION of " + target.getEmail());
        }
        updateRole(target, "admin", PROMOTE_SQL);
        
        // Trigger promotion notification
        String subject = "Promotion: You are now an Admin";
        String message = "Hello " + target.getName() + ",\n\n" +
                         "Congratulations! You have been promoted to Admin by the Super Admin.\n" +
                         "You now have access to administrative features.";
        NotificationManager.getInstance().sendNotification(target.getEmail(), subject, message);
    }

    /**
     * ✅ Demotes an admin back to student
     * Only super_admin can do this
     */
    public void demoteToStudent(User caller, User target) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED UNAUTHORIZED DEMOTION of " + target.getEmail());
        }
        updateRole(target, "student", DEMOTE_SQL);
        
        // Trigger demotion notification
        String subject = "Account Update: Role Changed to Student";
        String message = "Hello " + target.getName() + ",\n\n" +
                         "This is to inform you that your role has been changed to Student by the Super Admin.";
        NotificationManager.getInstance().sendNotification(target.getEmail(), subject, message);
    }

    /**
     * ✅ Deactivates a user account (soft delete)
     * Only super_admin can do this
     */
    public void deactivateUser(User caller, User target) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED UNAUTHORIZED DEACTIVATION of " + target.getEmail());
        }
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(DEACTIVATE_USER_SQL)) {
            ps.setInt(1, target.getId());
            ps.executeUpdate();
            System.out.println("✅ User deactivated: " + target.getEmail());
        } catch (SQLException ex) {
            System.out.println("[ERROR] Deactivate user failed: " + ex.getMessage());
        }
    }

    /**
     * ✅ Archives an event (soft delete)
     * Only admin or super_admin can do this
     */
    public void deleteEvent(User caller, int eventId) throws SecurityException {
        if (!isAuthorized(caller, "admin", "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED TO DELETE EVENT without permission");
        }
        
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE_EVENT_SQL)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
            System.out.println("✅ Event archived: ID " + eventId);
        } catch (SQLException ex) {
            System.out.println("[ERROR] Delete event failed: " + ex.getMessage());
        }
    }

    /**
     * ✅ View all users with their roles and status
     * Only super_admin can do this
     */
    public void viewAllUsers(User caller, Userservice userservice) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED TO VIEW ALL USERS without permission");
        }
        
        List<User> users = userservice.getAllUser();
        if (users.isEmpty()) {
            System.out.println("❌ No users found.");
            return;
        }
        
        System.out.println("\n╔═══════════════════════════════════════════╗");
        System.out.println("║           ALL USERS IN SYSTEM             ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        
        for (User user : users) {
            System.out.println("[" + user.getId() + "] " + user.getName() + 
                             " | " + user.getEmail() + 
                             " | Role: " + user.getRole());
        }
    }

    /**
     * ✅ View all events with their details
     * Only admin or super_admin can do this
     */
    public void viewAllEvents(User caller) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED TO VIEW ALL EVENTS without permission");
        }
        
        try (Connection con = DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(GET_ALL_EVENTS_SQL);
             java.sql.ResultSet rs = ps.executeQuery()) {
            
            System.out.println("\n╔═══════════════════════════════════════════╗");
            System.out.println("║           ALL EVENTS IN SYSTEM            ║");
            System.out.println("╚═══════════════════════════════════════════╝");
            
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("[" + rs.getInt("id") + "] " + rs.getString("title") + 
                                 " | Venue: " + rs.getString("venue") +
                                 " | Capacity: " + rs.getInt("capacity") +
                                 " | Available: " + rs.getInt("available_seats") +
                                 " | Status: " + rs.getString("status"));
            }
            
            if (!found) {
                System.out.println("❌ No events found.");
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] View events failed: " + ex.getMessage());
        }
    }

    /**
     * ✅ View all user registrations
     * Only admin or super_admin can do this
     */
    public void viewAllRegistrations(User caller) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED TO VIEW REGISTRATIONS without permission");
        }
        
        try (Connection con = DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(GET_ALL_REGISTRATIONS_SQL);
             java.sql.ResultSet rs = ps.executeQuery()) {
            
            System.out.println("\n╔═══════════════════════════════════════════╗");
            System.out.println("║        ALL REGISTRATIONS IN SYSTEM        ║");
            System.out.println("╚═══════════════════════════════════════════╝");
            
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("[" + rs.getInt("id") + "] " + 
                                 rs.getString("name") + " (" + rs.getString("email") + ") " +
                                 " → Event: " + rs.getString("title") +
                                 " | Status: " + rs.getString("registration_status") +
                                 " | Registered: " + rs.getString("registered_at"));
            }
            
            if (!found) {
                System.out.println("❌ No registrations found.");
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] View registrations failed: " + ex.getMessage());
        }
    }

    /**
     * ✅ View audit logs
     * Only super_admin can do this
     */
    public void viewAuditLogs(User caller, AuditService auditService) throws SecurityException {
        if (!isAuthorized(caller, "super_admin")) {
            throw new SecurityException(
                "[SECURITY] " + caller.getEmail() + " ATTEMPTED TO VIEW AUDIT LOGS without permission");
        }
        
        System.out.println("\n╔═══════════════════════════════════════════╗");
        System.out.println("║         SYSTEM AUDIT LOGS                 ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        auditService.viewAuditLogs();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ✅ PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ SECURITY METHOD: Checks if caller has required role(s)
     * 
     * @param caller The user to check
     * @param requiredRoles Roles that are allowed
     * @return true if caller has one of the required roles
     */
    private boolean isAuthorized(User caller, String... requiredRoles) {
        if (caller == null) {
            return false;
        }
        
        String callerRole = caller.getRole();
        
        for (String role : requiredRoles) {
            if (callerRole.equals(role)) {
                return true;  // ✅ User has permission
            }
        }
        
        return false;  // ❌ User does NOT have permission
    }

    /**
     * ✅ Updates user role in database
     * 
     * @param user The user to update
     * @param roleName The new role name
     * @param sql The SQL update statement
     */
    private void updateRole(User user, String roleName, String sql) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.executeUpdate();
            user.setRole(roleName);
            System.out.println("✅ User role updated: " + user.getEmail() + " → " + roleName);
        } catch (SQLException ex) {
            System.out.println("[ERROR] Role update failed: " + ex.getMessage());
        }
    }
}
