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
        server.createContext("/api/history", new HistoryHandler());

        // --- VOLUNTEER ENDPOINTS ---
        server.createContext("/api/clubs", new ClubsHandler());
        server.createContext("/api/clubs/members", new ClubMemberHandler());
        server.createContext("/api/users/role", new UserRoleHandler());
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
        server.createContext("/api/audit", new AuditLogsHandler());
        server.createContext("/api/qr/data", new QRDataHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("âœ… API Server running silently in background on http://localhost:8082");
    }

    // --- UTILS ---
    private static void setCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
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
        if (query == null || query.isEmpty())
            return params;
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
        private final service.Eventservice eventService = new service.Eventservice();
        private final service.Userservice userService = new service.Userservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange);
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());

            Integer categoryId = null;
            if (params.containsKey("categoryId")) {
                try {
                    categoryId = Integer.parseInt(params.get("categoryId"));
                } catch (Exception ignored) {
                }
            }
            String query = params.get("query");
            String sort = params.get("sort");

            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("[");

            try {
                List<model.Event> events = searchService.getEvents(categoryId, query, sort);

                boolean first = true;
                for (model.Event ev : events) {
                    if (!first)
                        jsonBuilder.append(",");
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

        private void handlePost(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);

                String title = json.get("title");
                String venue = json.get("venue");
                int capacity = Integer.parseInt(json.getOrDefault("capacity", "0"));
                int categoryId = Integer.parseInt(json.getOrDefault("categoryId", "0"));
                int adminId = Integer.parseInt(json.getOrDefault("adminId", "0"));
                String eventDateStr = json.get("eventDate");

                if (title == null || venue == null || eventDateStr == null) {
                    sendResponse(exchange, 400,
                            "{\"success\": false, \"message\": \"Title, Venue, and Date are required\"}");
                    return;
                }

                // Format: 2026-05-01T10:00 -> 2026-05-01 10:00:00
                String timestampStr = eventDateStr.replace("T", " ");
                if (timestampStr.length() == 16)
                    timestampStr += ":00";
                java.sql.Timestamp eventDate = java.sql.Timestamp.valueOf(timestampStr);

                model.Event newEvent = new model.Event(0, title, venue, capacity, adminId, categoryId, eventDate);
                eventService.createEvent(newEvent, adminId, userService);

                sendResponse(exchange, 200, "{\"success\": true, \"message\": \"Event created successfully!\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error: " + SimpleJson.escape(e.getMessage()) + "\"}");
            }
        }

        private void handleDelete(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                String eventIdStr = params.get("eventId");

                if (eventIdStr == null) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                    return;
                }

                int eventId = Integer.parseInt(eventIdStr);

                // Read caller info from body for permission check
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);
                String email = json.getOrDefault("email", "unknown");
                String role = json.getOrDefault("role", "student");

                // Mock user for Manager's check
                model.User caller = new model.User(0, "API User", email, "password", role);

                service.Manager manager = new service.Manager();
                manager.deleteEvent(caller, eventId);

                sendResponse(exchange, 200, "{\"success\": true, \"message\": \"Event archived successfully\"}");
            } catch (SecurityException se) {
                sendResponse(exchange, 403, "{\"error\": \"" + SimpleJson.escape(se.getMessage()) + "\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
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
                    if (!first)
                        jsonBuilder.append(",");
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
                            user.getName() + " just logged in via the frontend API.");

                    String resp = String.format(
                            "{\"success\": true, \"message\": \"Login successful\", \"role\": \"%s\", \"name\": \"%s\"}",
                            SimpleJson.escape(user.getRole()),
                            SimpleJson.escape(user.getName()));
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
                    sendResponse(exchange, 400,
                            "{\"success\": false, \"message\": \"All fields (name, email, password, confirmPassword, role) are required\"}");
                    return;
                }

                Authservice.SignupResult result = authService.signup(name, email, password, confirmPassword, role,
                        userService);

                String resp = String.format("{\"success\": %b, \"message\": \"%s\"}",
                        result.isSuccess(),
                        SimpleJson.escape(result.getMessage()));
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
                    if (!first)
                        jsonBuilder.append(",");
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

    static class UserRoleHandler implements HttpHandler {
        private final service.Manager manager = new service.Manager();
        private final Userservice userService = new Userservice();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                Map<String, String> json = SimpleJson.parse(body);
                String userIdStr = json.get("userId");
                String role = json.get("role");

                if (userIdStr == null || role == null) {
                    sendResponse(exchange, 400, "{\"error\": \"userId and role required\"}");
                    return;
                }

                User superAdmin = new User(1, "Super Admin", "superadmin@gmail.com", "super123", "super_admin");
                User target = new User(Integer.parseInt(userIdStr), "Target", "target@email.com", "pw", "student"); // Dummy target object for id
                
                // Fetch actual email for manager to update role based on email (manager methods use email internally if needed, wait, manager.promoteToAdmin uses User object)
                List<User> users = userService.getAllUser();
                for (User u : users) {
                    if (u.getId() == target.getId()) {
                        target = u;
                        break;
                    }
                }

                if ("admin".equalsIgnoreCase(role)) {
                    manager.promoteToAdmin(superAdmin, target);
                } else if ("student".equalsIgnoreCase(role)) {
                    manager.demoteToStudent(superAdmin, target);
                } else {
                    sendResponse(exchange, 400, "{\"error\": \"Invalid role\"}");
                    return;
                }

                sendResponse(exchange, 200, "{\"success\": true, \"message\": \"Role updated\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
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

            String sql = "SELECT r.id, r.user_id, r.event_id, u.name as user_name, u.email as user_email, e.title as event_title, r.registration_status, r.registered_at "
                    +
                    "FROM registrations r " +
                    "JOIN users u ON u.id = r.user_id " +
                    "JOIN events e ON e.id = r.event_id " +
                    "ORDER BY r.registered_at DESC";

            try (Connection con = DBConnection.getConnection();
                    PreparedStatement ps = con.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {

                boolean first = true;
                while (rs.next()) {
                    if (!first)
                        jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                            .append("\"id\":").append(rs.getInt("id")).append(",")
                            .append("\"userId\":").append(rs.getInt("user_id")).append(",")
                            .append("\"eventId\":").append(rs.getInt("event_id")).append(",")
                            .append("\"userName\":\"").append(SimpleJson.escape(rs.getString("user_name")))
                            .append("\",")
                            .append("\"userEmail\":\"").append(SimpleJson.escape(rs.getString("user_email")))
                            .append("\",")
                            .append("\"eventTitle\":\"").append(SimpleJson.escape(rs.getString("event_title")))
                            .append("\",")
                            .append("\"status\":\"").append(SimpleJson.escape(rs.getString("registration_status")))
                            .append("\",")
                            .append("\"registeredAt\":\"").append(SimpleJson.escape(rs.getString("registered_at")))
                            .append("\"")
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
                            success ? "Registered successfully" : "Registration failed");
                    sendResponse(exchange, success ? 200 : 400, resp);
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400,
                            "{\"success\": false, \"message\": \"userId and eventId must be numbers\"}");
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
                        "\"studentName\":"
                        + (result.getStudentName() != null ? "\"" + SimpleJson.escape(result.getStudentName()) + "\""
                                : "null")
                        + "," +
                        "\"eventTitle\":"
                        + (result.getEventTitle() != null ? "\"" + SimpleJson.escape(result.getEventTitle()) + "\""
                                : "null")
                        + "," +
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
                    if (!first)
                        jsonBuilder.append(",");
                    first = false;
                    jsonBuilder.append("{")
                            .append("\"id\":").append(f.getId()).append(",")
                            .append("\"userId\":").append(f.getUserId()).append(",")
                            .append("\"eventId\":").append(f.getEventId()).append(",")
                            .append("\"rating\":").append(f.getRating()).append(",")
                            .append("\"comment\":\"")
                            .append(SimpleJson.escape(f.getComment() != null ? f.getComment() : "")).append("\",")
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
                    sendResponse(exchange, 400,
                            "{\"success\": false, \"message\": \"userId, eventId, and rating required\"}");
                    return;
                }

                int userId = Integer.parseInt(userIdStr);
                int eventId = Integer.parseInt(eventIdStr);
                int rating = Integer.parseInt(ratingStr);

                String result = feedbackService.submitFeedback(userId, eventId, rating, comment != null ? comment : "");
                boolean success = result.contains("successfully");

                String resp = String.format("{\"success\": %b, \"message\": \"%s\"}", success,
                        SimpleJson.escape(result));
                sendResponse(exchange, success ? 200 : 400, resp);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400,
                        "{\"success\": false, \"message\": \"userId, eventId, and rating must be numbers\"}");
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
            if (handlePreflight(exchange))
                return;
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.ClubService cs = new service.ClubService();
                    String res = cs.createClub(data.get("name"), data.get("description"),
                            Integer.parseInt(data.getOrDefault("adminId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    service.ClubService cs = new service.ClubService();
                    StringBuilder sb = new StringBuilder("[");
                    for (model.Club c : cs.getAllClubs()) {
                        sb.append(String.format("{\"id\":%d, \"name\":\"%s\", \"desc\":\"%s\"},", c.getId(),
                                c.getName(), c.getDescription()));
                    }
                    if (sb.length() > 1)
                        sb.setLength(sb.length() - 1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
            }
        }
    }

    static class ClubMemberHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.ClubService cs = new service.ClubService();
                    String msg = cs.addMember(Integer.parseInt(data.get("clubId")), Integer.parseInt(data.get("userId")));
                    if (msg.contains("successfully")) {
                        sendResponse(exchange, 200, "{\"message\": \"Member added\"}");
                    } else {
                        sendResponse(exchange, 400, "{\"error\": \"Could not add member: " + msg + "\"}");
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerPolicyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.createPolicy(Integer.parseInt(data.get("eventId")), 
                            data.get("policyType"),
                            data.getOrDefault("appMode", "INDIVIDUAL"), 
                            Integer.parseInt(data.get("maxVolunteers")),
                            Integer.parseInt(data.getOrDefault("clubId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerTeamsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.createTeam(Integer.parseInt(data.get("eventId")), data.get("teamName"),
                            Integer.parseInt(data.get("maxMembers")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
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
                    for (model.VolunteerTeam t : vs.getTeams(eventId)) {
                        sb.append(String.format("{\"id\":%d, \"teamName\":\"%s\", \"max\":%d},", t.getId(),
                                t.getTeamName(), t.getMaxMembers()));
                    }
                    if (sb.length() > 1)
                        sb.setLength(sb.length() - 1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerApplyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.applyIndividual(Integer.parseInt(data.get("eventId")),
                            Integer.parseInt(data.get("userId")), Integer.parseInt(data.getOrDefault("teamId", "0")),
                            data.getOrDefault("note", ""));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerApplicationsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) { // Approve/Reject
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerService vs = new service.VolunteerService();
                    String res = vs.reviewApplication(Integer.parseInt(data.get("appId")), data.get("status"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
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
                    for (model.VolunteerApplication a : vs.getApplications(eventId)) {
                        sb.append(String.format("{\"id\":%d, \"userId\":%d, \"teamId\":%d, \"status\":\"%s\"},",
                                a.getId(), a.getUserId(), a.getTeamId(), a.getStatus()));
                    }
                    if (sb.length() > 1)
                        sb.setLength(sb.length() - 1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerTasksHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.createTask(Integer.parseInt(data.get("eventId")),
                            Integer.parseInt(data.getOrDefault("teamId", "0")),
                            Integer.parseInt(data.getOrDefault("assignedTo", "0")), data.get("title"),
                            data.get("description"), data.getOrDefault("priority", "1"), data.get("dueDate"),
                            Integer.parseInt(data.getOrDefault("adminId", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
                    String eventIdStr = params.get("eventId");
                    if (eventIdStr == null || eventIdStr.isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"eventId parameter required\"}");
                        return;
                    }
                    int eventId = Integer.parseInt(eventIdStr);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    List<model.VolunteerTask> tasks = ts.getTasksByEvent(eventId);
                    StringBuilder sb = new StringBuilder("[");
                    for (model.VolunteerTask t : tasks) {
                        sb.append(String.format("{\"id\":%d, \"eventId\":%d, \"teamId\":%d, \"assignedUserId\":%d, \"title\":\"%s\", \"description\":\"%s\", \"status\":\"%s\", \"priority\":\"%s\", \"dueDate\":\"%s\", \"createdAt\":\"%s\", \"updatedAt\":\"%s\"},",
                                t.getId(), t.getEventId(), t.getTeamId(), t.getAssignedTo(), 
                                SimpleJson.escape(t.getTitle()), SimpleJson.escape(t.getDescription()),
                                SimpleJson.escape(t.getStatus()), SimpleJson.escape(t.getPriority()),
                                SimpleJson.escape(t.getDueDate()), SimpleJson.escape(t.getCreatedAt()),
                                SimpleJson.escape(t.getUpdatedAt())));
                    }
                    if (sb.length() > 1) sb.setLength(sb.length() - 1);
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString());
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerTaskStatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.updateTaskStatus(Integer.parseInt(data.get("taskId")),
                            Integer.parseInt(data.get("userId")), data.get("status"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerTaskLogsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerTaskService ts = new service.VolunteerTaskService();
                    String res = ts.submitActivityLog(Integer.parseInt(data.get("taskId")),
                            Integer.parseInt(data.get("userId")), data.get("logText"),
                            Double.parseDouble(data.getOrDefault("hours", "0")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerCheckinHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerAttendanceService as = new service.VolunteerAttendanceService();
                    String res = as.checkIn(Integer.parseInt(data.get("eventId")), Integer.parseInt(data.get("userId")),
                            data.getOrDefault("method", "MANUAL"));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerCheckoutHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parseJson(body);
                    service.VolunteerAttendanceService as = new service.VolunteerAttendanceService();
                    String res = as.checkOut(Integer.parseInt(data.get("eventId")),
                            Integer.parseInt(data.get("userId")));
                    sendResponse(exchange, 200, "{\"message\": \"" + res + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerReportHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
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
                    if (sb.length() > 1)
                        sb.setLength(sb.length() - 1);
                    sb.append("}");
                    sendResponse(exchange, 200, sb.toString());
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId must be a number\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    static class VolunteerCertificateHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;
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
                    if (sb.length() > 1)
                        sb.setLength(sb.length() - 1);
                    sb.append("}");
                    sendResponse(exchange, 200, sb.toString());
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\": \"eventId and userId must be numbers\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"" + SimpleJson.escape(safeMessage(e)) + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }

    // --- HISTORY HANDLER ---
    static class HistoryHandler implements HttpHandler {
        private final service.AnalyticsService analyticsService = new service.AnalyticsService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String keyword = params.getOrDefault("query", "");

            try {
                List<String[]> history = analyticsService.getEventHistory(keyword);

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");

                boolean firstRow = true;
                for (String[] row : history) {
                    if (!firstRow)
                        jsonBuilder.append(",");
                    firstRow = false;

                    jsonBuilder.append("{")
                            .append("\"title\":\"").append(SimpleJson.escape(row[0])).append("\",")
                            .append("\"category\":\"").append(SimpleJson.escape(row[1])).append("\",")
                            .append("\"year\":\"").append(row[2]).append("\",")
                            .append("\"capacity\":").append(row[3]).append(",")
                            .append("\"registered\":").append(row[4]).append(",")
                            .append("\"fillPct\":\"").append(row[5]).append("\",")
                            .append("\"avgRating\":\"").append(row[6]).append("\",")
                            .append("\"totalReviews\":").append(row[7]).append(",")
                            .append("\"noShows\":").append(row[8])
                            .append("}");
                }

                jsonBuilder.append("]");
                sendResponse(exchange, 200, jsonBuilder.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- AUDIT LOGS HANDLER ---
    static class AuditLogsHandler implements HttpHandler {
        private final service.AuditService auditService = new service.AuditService();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange))
                return;

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                java.util.List<model.AuditLog> logs = auditService.getAuditLogs();

                StringBuilder jsonBuilder = new StringBuilder();
                jsonBuilder.append("[");

                boolean first = true;
                for (model.AuditLog log : logs) {
                    if (!first)
                        jsonBuilder.append(",");
                    first = false;

                    jsonBuilder.append("{")
                            .append("\"id\":").append(log.getId()).append(",")
                            .append("\"userEmail\":\"").append(SimpleJson.escape(log.getActorEmail())).append("\",")
                            .append("\"action\":\"").append(SimpleJson.escape(log.getAction() != null ? log.getAction() : "")).append("\",")
                            .append("\"details\":\"").append(SimpleJson.escape(log.getDetails() != null ? log.getDetails() : "")).append("\",")
                            .append("\"createdAt\":\"").append(SimpleJson.escape(log.getCreatedAt())).append("\"")
                            .append("}");
                }

                jsonBuilder.append("]");
                sendResponse(exchange, 200, jsonBuilder.toString());
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

    // --- QR DATA HANDLER (for student download) ---
    static class QRDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }
            Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
            String userIdStr = params.get("userId");
            String eventIdStr = params.get("eventId");
            if (userIdStr == null || eventIdStr == null) {
                sendResponse(exchange, 400, "{\"error\": \"userId and eventId required\"}");
                return;
            }
            try {
                int userId = Integer.parseInt(userIdStr);
                int eventId = Integer.parseInt(eventIdStr);
                String selectSql = "SELECT qr_data FROM qr_codes WHERE user_id = ? AND event_id = ?";
                try (Connection con = db.DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, eventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String qrData = rs.getString("qr_data");
                            sendResponse(exchange, 200, "{\"qrData\": \"" + SimpleJson.escape(qrData) + "\"}");
                        } else {
                            // QR not found — auto-generate for older registrations
                            // First verify the user is actually registered
                            String regCheck = "SELECT id FROM registrations WHERE user_id = ? AND event_id = ?";
                            try (PreparedStatement regPs = con.prepareStatement(regCheck)) {
                                regPs.setInt(1, userId);
                                regPs.setInt(2, eventId);
                                try (ResultSet regRs = regPs.executeQuery()) {
                                    if (!regRs.next()) {
                                        sendResponse(exchange, 404, "{\"error\": \"No registration found\"}");
                                        return;
                                    }
                                }
                            }
                            // Generate the same deterministic hash and save it
                            String qrData = service.QRService.generateHash(userId, eventId);
                            String insertSql = "INSERT INTO qr_codes (user_id, event_id, qr_data) VALUES (?, ?, ?)";
                            try (PreparedStatement insPs = con.prepareStatement(insertSql)) {
                                insPs.setInt(1, userId);
                                insPs.setInt(2, eventId);
                                insPs.setString(3, qrData);
                                insPs.executeUpdate();
                            } catch (java.sql.SQLException dupEx) {
                                // Ignore duplicate key errors
                            }
                            sendResponse(exchange, 200, "{\"qrData\": \"" + SimpleJson.escape(qrData) + "\"}");
                        }
                    }
                }
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "{\"error\": \"userId and eventId must be numbers\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"Server Error\"}");
            }
        }
    }

}
