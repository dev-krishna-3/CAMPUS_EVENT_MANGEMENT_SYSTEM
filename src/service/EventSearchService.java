package service;

import db.DBConnection;
import model.Event;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventSearchService {

    public List<Event> getEvents(Integer categoryId, String query, String sort) {
        List<Event> events = new ArrayList<>();
        
        // Base SQL with JOIN to categories
        StringBuilder sql = new StringBuilder(
            "SELECT e.id, e.title, e.venue, e.capacity, e.available_seats, e.status, e.category_id, e.event_date, c.name as category_name " +
            "FROM events e " +
            "LEFT JOIN categories c ON e.category_id = c.id " +
            "WHERE e.status <> 'archived' "
        );

        // Dynamic Filtering
        if (categoryId != null && categoryId > 0) {
            sql.append(" AND e.category_id = ? ");
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND e.title LIKE ? ");
        }

        // Dynamic Sorting
        if ("date_asc".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY e.event_date ASC ");
        } else if ("date_desc".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY e.event_date DESC ");
        } else {
            sql.append(" ORDER BY e.id DESC "); // default
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            if (categoryId != null && categoryId > 0) {
                ps.setInt(paramIndex++, categoryId);
            }
            if (query != null && !query.trim().isEmpty()) {
                ps.setString(paramIndex++, "%" + query.trim() + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new Event(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("venue"),
                        rs.getInt("capacity"),
                        rs.getInt("available_seats"),
                        0, // createdBy not needed for list
                        rs.getInt("category_id"),
                        rs.getTimestamp("event_date")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Event search failed: " + e.getMessage());
        }
        return events;
    }
}
