package service;

import db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service for event analytics, health scores, and year-on-year comparisons.
 */
public class AnalyticsService {

    // Fill rate uses available_seats directly (no JOIN needed)
    private static final String FILL_RATE_SQL =
            "SELECT capacity, available_seats FROM events WHERE id = ?";

    // No-show rate uses registration_status (Method 2 — cleaner)
    private static final String NO_SHOW_SQL =
            "SELECT " +
            "COUNT(*) AS total_registered, " +
            "SUM(CASE WHEN registration_status = 'registered' THEN 1 ELSE 0 END) AS no_shows " +
            "FROM registrations WHERE event_id = ?";

    // Review count for health score
    private static final String REVIEW_COUNT_SQL =
            "SELECT COUNT(*) AS cnt FROM feedback WHERE event_id = ?";

    // Year-on-year history with category
    private static final String EVENT_HISTORY_SQL =
            "SELECT " +
            "e.id, e.title, c.name AS category, YEAR(e.event_date) AS year, " +
            "e.capacity, (e.capacity - e.available_seats) AS registered, " +
            "ROUND((e.capacity - e.available_seats) / e.capacity * 100, 1) AS fill_pct, " +
            "ROUND(AVG(f.rating), 1) AS avg_rating, " +
            "COUNT(f.id) AS total_reviews, " +
            "SUM(CASE WHEN r.registration_status = 'registered' THEN 1 ELSE 0 END) AS no_shows " +
            "FROM events e " +
            "LEFT JOIN categories c ON c.id = e.category_id " +
            "LEFT JOIN feedback f ON f.event_id = e.id " +
            "LEFT JOIN registrations r ON r.event_id = e.id " +
            "WHERE e.title LIKE ? " +
            "GROUP BY e.id, YEAR(e.event_date) " +
            "ORDER BY year DESC";

    // Improvement tips
    private static final HashMap<String, String> TIPS = new HashMap<>();
    static {
        TIPS.put("LOW_FILL",     "Increase promotion 2 weeks earlier");
        TIPS.put("LOW_RATING",   "Review venue and speaker quality");
        TIPS.put("HIGH_NOSHOW",  "Send reminder email 1 day before");
        TIPS.put("LOW_FEEDBACK", "Encourage students to submit reviews");
        TIPS.put("LOW_CATEGORY", "This category underperforms — consider merging with popular ones");
    }

    // ==================== 1. EVENT HISTORY ====================
    /**
     * Year-on-year comparison for events matching a title keyword.
     */
    public List<String[]> getEventHistory(String titleKeyword) {
        List<String[]> rows = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(EVENT_HISTORY_SQL)) {
            ps.setString(1, "%" + titleKeyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[] {
                        rs.getString("title"),
                        rs.getString("category") != null ? rs.getString("category") : "N/A",
                        String.valueOf(rs.getInt("year")),
                        String.valueOf(rs.getInt("capacity")),
                        String.valueOf(rs.getInt("registered")),
                        String.valueOf(rs.getDouble("fill_pct")) + "%",
                        String.valueOf(rs.getDouble("avg_rating")),
                        String.valueOf(rs.getInt("total_reviews")),
                        String.valueOf(rs.getInt("no_shows"))
                    };
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsService] Error fetching history: " + e.getMessage());
        }
        return rows;
    }

    // ==================== 2. FILL RATE ====================
    /**
     * Returns fill rate as a percentage.
     */
    public double getFillRate(int eventId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(FILL_RATE_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int capacity = rs.getInt("capacity");
                    int availableSeats = rs.getInt("available_seats");
                    if (capacity == 0) return 0.0;
                    return ((capacity - availableSeats) / (double) capacity) * 100;
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsService] Error getting fill rate: " + e.getMessage());
        }
        return 0.0;
    }

    // ==================== 3. NO-SHOW RATE ====================
    /**
     * Returns no-show count (registered but never attended).
     */
    public int getNoShowCount(int eventId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(NO_SHOW_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("no_shows");
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsService] Error getting no-show rate: " + e.getMessage());
        }
        return 0;
    }

    // ==================== 4. HEALTH SCORE ====================
    /**
     * Calculates event health score: 40% fill + 40% rating + 20% feedback.
     */
    public int calculateHealthScore(int eventId) {
        double fillRate = getFillRate(eventId);

        FeedbackService fs = new FeedbackService();
        double avgRating = fs.getAverageRating(eventId);

        int reviewCount = 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(REVIEW_COUNT_SQL)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reviewCount = rs.getInt("cnt");
                }
            }
        } catch (SQLException e) {
            System.err.println("[AnalyticsService] Error getting review count: " + e.getMessage());
        }

        double fillScore     = fillRate * 0.40;
        double ratingScore   = (avgRating / 5.0) * 100 * 0.40;
        double feedbackScore = Math.min(reviewCount / 20.0, 1.0) * 100 * 0.20;
        return (int)(fillScore + ratingScore + feedbackScore);
    }

    // ==================== 5. BADGE ====================
    /**
     * Assigns a badge based on health score.
     */
    public String assignBadge(int healthScore) {
        if (healthScore >= 80) return "🥇 GOLD";
        if (healthScore >= 60) return "🥈 SILVER";
        if (healthScore >= 40) return "🥉 BRONZE";
        return "🚩 RED FLAG";
    }

    // ==================== 6. IMPROVEMENT TIP ====================
    /**
     * Returns an actionable improvement tip based on metrics.
     */
    public String getImprovementTip(double fillRate, double avgRating, int noShows) {
        if (fillRate < 50) return TIPS.get("LOW_FILL");
        if (avgRating < 3.0) return TIPS.get("LOW_RATING");
        if (noShows > 10) return TIPS.get("HIGH_NOSHOW");
        if (avgRating < 4.0) return TIPS.get("LOW_CATEGORY");
        return "Event is performing well! Keep it up.";
    }
}
