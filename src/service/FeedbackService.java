package service;

import db.DBConnection;
import model.Feedback;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service for managing event feedback and ratings.
 */
public class FeedbackService {

    private static final String INSERT_FEEDBACK_SQL =
            "INSERT INTO feedback (user_id, event_id, rating, comment) VALUES (?, ?, ?, ?)";
    private static final String GET_FEEDBACK_BY_EVENT_SQL =
            "SELECT f.id, f.user_id, f.event_id, f.rating, f.comment, f.created_at, u.name AS user_name " +
            "FROM feedback f JOIN users u ON u.id = f.user_id " +
            "WHERE f.event_id = ? ORDER BY f.created_at DESC";
    private static final String AVG_RATING_SQL =
            "SELECT AVG(rating) AS avg_rating FROM feedback WHERE event_id = ?";
    private static final String RATING_DISTRIBUTION_SQL =
            "SELECT rating, COUNT(*) AS cnt FROM feedback WHERE event_id = ? GROUP BY rating ORDER BY rating";

    /**
     * Submit feedback for an event. Validates rating 1-5 and checks uniqueness.
     */
    public String submitFeedback(int userId, int eventId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            return "Rating must be between 1 and 5.";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_FEEDBACK_SQL)) {
            ps.setInt(1, userId);
            ps.setInt(2, eventId);
            ps.setInt(3, rating);
            ps.setString(4, comment);
            ps.executeUpdate();
            return "Feedback submitted successfully!";
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                return "You have already submitted feedback for this event.";
            }
            return "Failed to submit feedback: " + e.getMessage();
        }
    }

    /**
     * Get all feedback for a specific event.
     */
    public List<Feedback> getFeedbackByEvent(int eventId) {
        List<Feedback> feedbackList = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_FEEDBACK_BY_EVENT_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Feedback f = new Feedback(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getInt("event_id"),
                        rs.getInt("rating"),
                        rs.getString("comment"),
                        rs.getString("created_at")
                    );
                    feedbackList.add(f);
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackService] Error fetching feedback: " + e.getMessage());
        }
        return feedbackList;
    }

    /**
     * Get the average rating for an event.
     */
    public double getAverageRating(int eventId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(AVG_RATING_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("avg_rating");
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackService] Error getting avg rating: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Get rating distribution (how many 1-star, 2-star, etc.)
     */
    public HashMap<Integer, Integer> getRatingDistribution(int eventId) {
        HashMap<Integer, Integer> distribution = new HashMap<>();
        // Initialize all ratings to 0
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0);
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(RATING_DISTRIBUTION_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    distribution.put(rs.getInt("rating"), rs.getInt("cnt"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FeedbackService] Error getting distribution: " + e.getMessage());
        }
        return distribution;
    }
}
