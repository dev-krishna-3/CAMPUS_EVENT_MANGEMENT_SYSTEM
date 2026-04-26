package service;

import db.DBConnection;
import model.Event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Eventservice {
    private static final String INSERT_EVENT_SQL =
            "INSERT INTO events(title, venue, capacity, available_seats, created_by, status, category_id, event_date) VALUES (?, ?, ?, ?, ?, 'open', ?, ?)";

    private static final String LIST_EVENTS_SQL =
                    "SELECT id, title, venue, capacity, available_seats, category_id, event_date FROM events WHERE status <> 'archived' ORDER BY id DESC";

    public void createEvent(Event e, int adminId, Userservice us) {
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(INSERT_EVENT_SQL)) {
            ps.setString(1, e.getTitle());
            ps.setString(2, e.getVenue());
            ps.setInt(3, e.getCapacity());
            ps.setInt(4, e.getCapacity());
            ps.setInt(5, adminId);
            ps.setInt(6, e.getCategoryId());
            ps.setTimestamp(7, e.getEventDate());
            ps.executeUpdate();
            System.out.println("Event created successfully!");
            
            // Broadcast to all students
            broadcastNewEvent(e, us);
        } catch (SQLException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    public void viewEvents(){
        List<Event> events = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(LIST_EVENTS_SQL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(new Event(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("venue"),
                        rs.getInt("capacity"),
                        rs.getInt("available_seats"),
                        0,
                        rs.getInt("category_id"),
                        rs.getTimestamp("event_date")));
            }

            printEvents(events);
        } catch (SQLException ex) {
            System.out.println("[ERROR] viewEvents failed: " + ex.getMessage());
            printEvents(events);
        }
    }

    private void printEvents(List<Event> events) {
        if (events.isEmpty()) {
            System.out.println("No events found.");
            return;
        }

        for (Event e : events) {
            System.out.println("[" + e.getId() + "] " + e.getTitle() + " | " + e.getVenue() + " | Available Seats: " + e.getAvailableSeats() + "/" + e.getCapacity());
        }
    }

    public void viewMyEvents(int adminId) {
        String sql = "SELECT id, title, venue, capacity, available_seats FROM events "
                + "WHERE created_by = ? AND status != 'archived' ORDER BY id DESC";

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n==============================");
                System.out.println("MY EVENTS (Created by Me)");
                System.out.println("==============================");

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("[" + rs.getInt("id") + "] "
                            + rs.getString("title") + " | "
                            + rs.getString("venue") + " | Available: "
                            + rs.getInt("available_seats"));
                }

                if (!found) {
                    System.out.println("No events created by you yet.");
                }
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    public boolean isEventCreatedBy(int eventId, int adminId) {
        String sql = "SELECT created_by FROM events WHERE id = ?";

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("created_by") == adminId;
                }
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
        return false;
    }

    public void viewMyEventRegistrations(int adminId) {
        String sql = "SELECT r.id, u.name, u.email, e.title, r.registration_status "
                + "FROM registrations r "
                + "JOIN users u ON u.id = r.user_id "
                + "JOIN events e ON e.id = r.event_id "
                + "WHERE e.created_by = ? ORDER BY r.registered_at DESC";

        try (Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n==============================");
                System.out.println("REGISTRATIONS FOR MY EVENTS");
                System.out.println("==============================");

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("[" + rs.getInt("id") + "] "
                            + rs.getString("name") + " -> "
                            + rs.getString("title") + " | "
                            + rs.getString("registration_status"));
                }

                if (!found) {
                    System.out.println("No registrations for your events.");
                }
            }
        } catch (SQLException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    private void broadcastNewEvent(Event e, Userservice us) {
        java.util.List<String> studentEmails = us.getAllStudentEmails();
        String subject = "New Upcoming Event: " + e.getTitle();
        String message = "Hi Student,\n\n" +
                         "A new event has been posted: " + e.getTitle() + "\n" +
                         "Venue: " + e.getVenue() + "\n" +
                         "Capacity: " + e.getCapacity() + " seats available.\n\n" +
                         "Register soon before it gets full!";
        
        for (String email : studentEmails) {
            NotificationManager.getInstance().sendNotification(email, subject, message);
        }
    }
}
