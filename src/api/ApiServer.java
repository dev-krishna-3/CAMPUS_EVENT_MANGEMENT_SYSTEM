package api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import db.DBConnection;
import db.DatabaseInitializer;
import service.Authservice;
import service.Userservice;
import service.NotificationService;
import model.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ApiServer {

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);

        // Define API Endpoints
        server.createContext("/api/categories", new CategoriesHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/registrations", new RegistrationsHandler());
        server.createContext("/api/qr/scan", new QRScanHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/feedback", new FeedbackHandler());

        // --- VOLUNTEER ENDPOINTS ---
        server.createContext("/api/clubs", new ClubsHandler());
        server.createContext("/api/volunteer/policy", new VolunteerPolicyHandler());
        server.createContext("/api/volunteer/teams", new VolunteerTeamsHandler());
        server.createContext("/api/volunteer/apply", new VolunteerApplyHandler());
        server.createContext("/api/volunteer/applications", new VolunteerApplicationsHandler());
        server.createContext("/api/volunteer/tasks", new VolunteerTasksHandler());
        server.createContext("/api/volunteer/tasks/status", new VolunteerTaskStatusHandler());
        server.createContext("/api/volunteer/tasks/logs", new VolunteerTaskLogsHandler());
        server.createContext("/api/volunteer/attendance/checkin", new VolunteerCheckinHandler());
        server.createContext("/api/volunteer/attendance/checkout", new VolunteerCheckoutHandler());
        server.createContext("/api/volunteer/report", new VolunteerReportHandler());
        server.createContext("/api/volunteer/certificate", new VolunteerCertificateHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("âœ… API Server running silently in background on http://localhost:8082");
    }

    // --- UTILS ---
    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        setCORSHeaders(exchange);
        byte[] bytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> getQueryParams(String query) {
        Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            if (kv.length > 1) {
                params.put(key, URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else {
                params.put(key, "");
            }
        }
        return params;
    }

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return true;
        }
        return false;
    }

    private static String safeMessage(Throwable t) {
        if (t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()) {
            return "Server Error";
        }
        return t.getMessage();
    }

    // --- HANDLERS ---

    static class EventsHandler implements HttpHandler {
        private final service.EventSearchService searchService = new service.EventSearchService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            
            Integer categoryId = null;
            if (params.containsKey("categoryId")) {
               try { categoryId = Integer.parseInt(params.get("categoryId")); } catch (Exception ignored) {}
            }
            String query = params.get("query");
            String sort = params.get("sort");

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");

            try {
                List<model.Event> events = searchService.getEvents(categoryId, query, sort);
                
                boolean first = true;
                for (model.Event ev : events) {
                    if (!first) jsonBuilder.append(",");
                    first = false;

                    jsonBuilder.append("{")
                        .append("\"id\":").append(ev.getId()).append(",")
                        .append("\"title\":\"").append(SimpleJson.escape(ev.getTitle())).append("\",")
                        .append("\"venue\":\"").append(SimpleJson.escape(ev.getVenue())).append("\",")
                        .append("\"capacity\":").append(ev.getCapacity()).append(",")
                        .append("\"availableSeats\":").append(ev.getAvailableSeats()).append(",")
                        .append("\"categoryId\":").append(ev.getCategoryId()).append(",")
                        .append("\"eventDate\":\"").append(ev.getEventDate()).append("\",")
                        .append("\"status\":\"open\"")
                        .append("}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
                return;
            }

            jsonBuilder.append("]");
            sendResponse(exchange, 200, jsonBuilder.toString());
        }
    }

    static class CategoriesHandler implements HttpHandler {
        private final service.CategoryService categoryService = new service.CategoryService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");

            try {
                List<model.Category> categories = categoryService.getAllCategories();
                boolean first = true;
                for (model.Category c : categories) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                        .append("\"id\":").append(c.getId()).append(",")
                        .append("\"name\":\"").append(SimpleJson.escape(c.getName())).append("\",")
                        .append("\"description\":\"").append(SimpleJson.escape(c.getDescription())).append("\"")
                        .append("}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
                return;
            }

            jsonBuilder.append("]");
            sendResponse(exchange, 200, jsonBuilder.toString());
        }
    }

    static class LoginHandler implements HttpHandler {
        private final Authservice authService = new Authservice();
        private final Userservice userService = new Userservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String email = json.get("email");
                String password = json.get("password");

                if (email == null || password == null) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"Email and password required\"}");
                    return;
                }

                User user = authService.login(email, password, userService);
                
                if (user != null) {
                    // Trigger Native Windows Popup
                    NotificationService.showNotification(
                       "New API Login! ðŸš€", 
                       user.getName() + " just logged in via the frontend API."
                    );
                    
                    String resp = String.format("{\"success\": true, \"message\": \"Login successful\", \"role\": \"%s\", \"name\": \"%s\"}", 
                        SimpleJson.escape(user.getRole()), 
                        SimpleJson.escape(user.getName())
                    );
                    sendResponse(exchange, 200, resp);
                } else {
                    sendResponse(exchange, 401, "{\"success\": false, \"message\": \"Invalid credentials\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        private final Authservice authService = new Authservice();
        private final Userservice userService = new Userservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String name = json.get("name");
                String email = json.get("email");
                String password = json.get("password");
                String confirmPassword = json.get("confirmPassword");
                String role = json.get("role");

                if (name == null || email == null || password == null || confirmPassword == null || role == null) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"All fields (name, email, password, confirmPassword, role) are required\"}");
                    return;
                }

                Authservice.SignupResult result = authService.signup(name, email, password, confirmPassword, role, userService);
                
                String resp = String.format("{\"success\": %b, \"message\": \"%s\"}", 
                    result.isSuccess(), 
                    SimpleJson.escape(result.getMessage())
                );
                sendResponse(exchange, result.isSuccess() ? 200 : 400, resp);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    static class UsersHandler implements HttpHandler {
        private final Userservice userService = new Userservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");
            
            try {
                List<User> users = userService.getAllUser();
                boolean first = true;
                for (User u : users) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                        .append("\"id\":").append(u.getId()).append(",")
                        .append("\"name\":\"").append(SimpleJson.escape(u.getName())).append("\",")
                        .append("\"email\":\"").append(SimpleJson.escape(u.getEmail())).append("\",")
                        .append("\"role\":\"").append(SimpleJson.escape(u.getRole())).append("\"")
                        .append("}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
                return;
            }

            jsonBuilder.append("]");
            sendResponse(exchange, 200, jsonBuilder.toString());
        }
    }

    static class RegistrationsHandler implements HttpHandler {
        private final service.Registrationservice registrationService = new service.Registrationservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");

            String sql = "SELECT r.id, r.user_id, r.event_id, u.name as user_name, u.email as user_email, e.title as event_title, r.registration_status, r.registered_at " +
                         "FROM registrations r " +
                         "JOIN users u ON u.id = r.user_id " +
                         "JOIN events e ON e.id = r.event_id " +
                         "ORDER BY r.registered_at DESC";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"userId\":").append(rs.getInt("user_id")).append(",")
                        .append("\"eventId\":").append(rs.getInt("event_id")).append(",")
                        .append("\"userName\":\"").append(SimpleJson.escape(rs.getString("user_name"))).append("\",")
                        .append("\"userEmail\":\"").append(SimpleJson.escape(rs.getString("user_email"))).append("\",")
                        .append("\"eventTitle\":\"").append(SimpleJson.escape(rs.getString("event_title"))).append("\",")
                        .append("\"status\":\"").append(SimpleJson.escape(rs.getString("registration_status"))).append("\",")
                        .append("\"registeredAt\":\"").append(SimpleJson.escape(rs.getString("registered_at"))).append("\"")
                        .append("}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
                return;
            }

            jsonBuilder.append("]");
            sendResponse(exchange, 200, jsonBuilder.toString());
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String userIdStr = json.get("userId");
                String eventIdStr = json.get("eventId");

                if (userIdStr == null || eventIdStr == null) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"userId and eventId required\"}");
                    return;
                }

                try {
                    int userId = Integer.parseInt(userIdStr);
                    int eventId = Integer.parseInt(eventIdStr);

                    boolean success = registrationService.register(userId, eventId);
                    
                    String resp = String.format("{\"success\": %b, \"message\": \"%s\"}", 
                        success, 
                        success ? "Registered successfully" : "Registration failed"
                    );
                    sendResponse(exchange, success ? 200 : 400, resp);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"userId and eventId must be numbers\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- QR SCAN HANDLER ---
    static class QRScanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String qrData = json.get("qrData");
                if (qrData == null || qrData.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"qrData is required\"}");
                    return;
                }

                service.QRService.ScanResult result = service.QRService.scanQR(qrData.trim());

                String resp = "{" +
                    "\"success\":" + result.isSuccess() + "," +
                    "\"message\":\"" + SimpleJson.escape(result.getMessage()) + "\"," +
                    "\"studentName\":" + (result.getStudentName() != null ? "\"" + SimpleJson.escape(result.getStudentName()) + "\"" : "null") + "," +
                    "\"eventTitle\":" + (result.getEventTitle() != null ? "\"" + SimpleJson.escape(result.getEventTitle()) + "\"" : "null") + "," +
                    "\"remainingSeats\":" + result.getRemainingSeats() +
                    "}";

                sendResponse(exchange, result.isSuccess() ? 200 : 400, resp);
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- ANALYTICS HANDLER ---
    static class AnalyticsHandler implements HttpHandler {
        private final service.AnalyticsService analyticsService = new service.AnalyticsService();
        private final service.FeedbackService feedbackService = new service.FeedbackService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            // Extract event ID from query: /api/analytics?eventId=5
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String eventIdStr = params.get("eventId");

            if (eventIdStr == null) {
                sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                return;
            }

            try {
                int eventId = Integer.parseInt(eventIdStr);
                double fillRate = analyticsService.getFillRate(eventId);
                double avgRating = feedbackService.getAverageRating(eventId);
                int healthScore = analyticsService.calculateHealthScore(eventId);
                String badge = analyticsService.assignBadge(healthScore);
                int noShows = analyticsService.getNoShowCount(eventId);
                String tip = analyticsService.getImprovementTip(fillRate, avgRating, noShows);

                String resp = "{" +
                    "\"eventId\":" + eventId + "," +
                    "\"fillRate\":" + String.format("%.1f", fillRate) + "," +
                    "\"avgRating\":" + String.format("%.1f", avgRating) + "," +
                    "\"healthScore\":" + healthScore + "," +
                    "\"badge\":\"" + SimpleJson.escape(badge) + "\"," +
                    "\"noShows\":" + noShows + "," +
                    "\"tip\":\"" + SimpleJson.escape(tip) + "\"" +
                    "}";

                sendResponse(exchange, 200, resp);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- FEEDBACK HANDLER ---
    static class FeedbackHandler implements HttpHandler {
        private final service.FeedbackService feedbackService = new service.FeedbackService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String eventIdStr = params.get("eventId");

            if (eventIdStr == null) {
                sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                return;
            }

            try {
                int eventId = Integer.parseInt(eventIdStr);
                java.util.List<model.Feedback> feedbackList = feedbackService.getFeedbackByEvent(eventId);

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");
                boolean first = true;
                for (model.Feedback f : feedbackList) {
                    if (!first) jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                        .append("\"id\":").append(f.getId()).append(",")
                        .append("\"userId\":").append(f.getUserId()).append(",")
                        .append("\"eventId\":").append(f.getEventId()).append(",")
                        .append("\"rating\":").append(f.getRating()).append(",")
                        .append("\"comment\":\"").append(SimpleJson.escape(f.getComment() != null ? f.getComment() : "")).append("\",")
                        .append("\"createdAt\":\"").append(SimpleJson.escape(f.getCreatedAt())).append("\"")
                        .append("}");
                }
                jsonBuilder.append("]");
                sendResponse(exchange, 200, jsonBuilder.toString());
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String userIdStr = json.get("userId");
                String eventIdStr = json.get("eventId");
                String ratingStr = json.get("rating");
                String comment = json.get("comment");

                if (userIdStr == null || eventIdStr == null || ratingStr == null) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"userId, eventId, and rating required\"}");
                    return;
                }

                int userId = Integer.parseInt(userIdStr);
                int eventId = Integer.parseInt(eventIdStr);
                int rating = Integer.parseInt(ratingStr);

                String result = feedbackService.submitFeedback(userId, eventId, rating, comment != null ? comment : "");
                boolean success = result.contains("successfully");

                String resp = String.format("{\"success\": %b, \"message\": \"%s\"}", success, SimpleJson.escape(result));
                sendResponse(exchange, success ? 200 : 400, resp);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"success\": false, \"message\": \"userId, eventId, and rating must be numbers\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- UTILITY ---
    private static Map<String, String> parseJson(String json) {
        return SimpleJson.parse(json);
    }

    // --- VOLUNTEER ENDPOINTS ---
    static class ClubsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.ClubService cs = new service.ClubService();
                    String res = cs.createClub(data.get("name"), data.get("description"), Integer.parseInt(data.getOrDefault("adminId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    service.ClubService cs = new service.ClubService();
                    StringBuilder sb = new StringBuilder("[");
                    for(model.Club c : cs.getAllClubs()) {
                        sb.append(String.format("{\"id\":%d, \"name\":\"%s\", \"desc\":\"%s\"},", c.getId(), c.getName(), c.getDescription()));
                    }
                    if(sb.length() > 1) sb.setLength(sb.length()-1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
            } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
        }
    }

    static class VolunteerPolicyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.createPolicy(Integer.parseInt(data.get("eventId")), data.get("policyType"), data.get("appMode"), Integer.parseInt(data.get("maxVolunteers")), Integer.parseInt(data.getOrDefault("clubId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerTeamsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.createTeam(Integer.parseInt(data.get("eventId")), data.get("teamName"), Integer.parseInt(data.get("maxMembers")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                    String eventIdStr = params.get("eventId");
                    if (eventIdStr == null || eventIdStr.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                        return;
                    }
                    int eventId = Integer.parseInt(eventIdStr);
                    service.VolunteerService vs = new service.VolunteerService();
                    StringBuilder sb = new StringBuilder("[");
                    for(model.VolunteerTeam t : vs.getTeams(eventId)) {
                        sb.append(String.format("{\"id\":%d, \"teamName\":\"%s\", \"max\":%d},", t.getId(), t.getTeamName(), t.getMaxMembers()));
                    }
                    if(sb.length() > 1) sb.setLength(sb.length()-1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch(NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch(Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}");
                }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerApplyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.applyIndividual(Integer.parseInt(data.get("eventId")), Integer.parseInt(data.get("userId")), Integer.parseInt(data.getOrDefault("teamId", "0")), data.getOrDefault("note", ""));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerApplicationsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) { // Approve/Reject
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.reviewApplication(Integer.parseInt(data.get("appId")), data.get("status"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else if ("GET".equals(exchange.getRequestMethod())) { // List
                try {
                    Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                    String eventIdStr = params.get("eventId");
                    if (eventIdStr == null || eventIdStr.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                        return;
                    }
                    int eventId = Integer.parseInt(eventIdStr);
                    service.VolunteerService vs = new service.VolunteerService();
                    StringBuilder sb = new StringBuilder("[");
                    for(model.VolunteerApplication a : vs.getApplications(eventId)) {
                        sb.append(String.format("{\"id\":%d, \"userId\":%d, \"teamId\":%d, \"status\":\"%s\"},", a.getId(), a.getUserId(), a.getTeamId(), a.getStatus()));
                    }
                    if(sb.length() > 1) sb.setLength(sb.length()-1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch(NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch(Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}");
                }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerTasksHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.createTask(Integer.parseInt(data.get("eventId")), Integer.parseInt(data.getOrDefault("teamId", "0")), Integer.parseInt(data.getOrDefault("assignedTo", "0")), data.get("title"), data.get("description"), data.getOrDefault("priority", "1"), data.get("dueDate"), Integer.parseInt(data.getOrDefault("adminId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerTaskStatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.updateTaskStatus(Integer.parseInt(data.get("taskId")), Integer.parseInt(data.get("userId")), data.get("status"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerTaskLogsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.submitActivityLog(Integer.parseInt(data.get("taskId")), Integer.parseInt(data.get("userId")), data.get("logText"), Double.parseDouble(data.getOrDefault("hours", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerCheckinHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerAttendanceService as = new service.VolunteerAttendanceService();
                    String res = as.checkIn(Integer.parseInt(data.get("eventId")), Integer.parseInt(data.get("userId")), data.getOrDefault("method", "MANUAL"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerCheckoutHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerAttendanceService as = new service.VolunteerAttendanceService();
                    String res = as.checkOut(Integer.parseInt(data.get("eventId")), Integer.parseInt(data.get("userId")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch(Exception e) { sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}"); }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerReportHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                    String eventIdStr = params.get("eventId");
                    if (eventIdStr == null || eventIdStr.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                        return;
                    }
                    int eventId = Integer.parseInt(eventIdStr);
                    service.VolunteerReportService rs = new service.VolunteerReportService();
                    java.util.HashMap<String, String> report = rs.getEventSummary(eventId);
                    StringBuilder sb = new StringBuilder("{");
                    for (Map.Entry<String, String> e : report.entrySet()) {
                        sb.append(String.format("\"%s\":\"%s\",", e.getKey(), e.getValue()));
                    }
                    if(sb.length() > 1) sb.setLength(sb.length()-1);
                    sb.append("}");
                    sendResponse(exchange, 200, sb.toString());
                } catch(NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch(Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}");
                }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

    static class VolunteerCertificateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                    String eventIdStr = params.get("eventId");
                    String userIdStr = params.get("userId");
                    if (eventIdStr == null || eventIdStr.isEmpty() || userIdStr == null || userIdStr.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"eventId and userId parameters required\"}");
                        return;
                    }
                    int eventId = Integer.parseInt(eventIdStr);
                    int userId = Integer.parseInt(userIdStr);
                    service.VolunteerReportService rs = new service.VolunteerReportService();
                    java.util.HashMap<String, String> cert = rs.checkCertificateEligibility(eventId, userId);
                    StringBuilder sb = new StringBuilder("{");
                    for (Map.Entry<String, String> e : cert.entrySet()) {
                        sb.append(String.format("\"%s\":\"%s\",", e.getKey(), e.getValue()));
                    }
                    if(sb.length() > 1) sb.setLength(sb.length()-1);
                    sb.append("}");
                    sendResponse(exchange, 200, sb.toString());
                } catch(NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId and userId must be numbers\"}");
                } catch(Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \""+SimpleJson.escape(safeMessage(e))+"\"}");
                }
            } else { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); }
        }
    }

}
