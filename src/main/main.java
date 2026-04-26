package main;

import db.DatabaseInitializer;
import model.*;
import service.*;
import java.util.Scanner;

public class main {
    public static void main(String arg[]) {
        if (!DatabaseInitializer.initialize()) {
            System.out.println("Application stopped because database is not connected.");
            return;
        }

        try {
            api.ApiServer.startServer();
        } catch (Exception e) {
            System.out.println("⚠️ Warning: API Server failed to start: " + e.getMessage());
        }

        Scanner sc = new Scanner(System.in);
        Userservice us = new Userservice();
        Authservice as = new Authservice();
        Eventservice es = new Eventservice();
        Registrationservice rs = new Registrationservice();
        AuditService auditService = new AuditService();
        Manager manager = new Manager();
        CategoryService cs = new CategoryService();
        EventSearchService searchService = new EventSearchService();
        ClubService clubService = new ClubService();
        VolunteerService volunteerService = new VolunteerService();
        VolunteerTaskService taskService = new VolunteerTaskService();
        VolunteerAttendanceService attendanceService = new VolunteerAttendanceService();
        VolunteerReportService reportService = new VolunteerReportService();

        // Initialize default users (only once - database handles duplicates via ON
        // DUPLICATE KEY)
        us.addUser(new User(1, "Krishna", "k@gmail.com", "123", "student"));
        us.addUser(new User(2, "Admin", "admin@gmail.com", "admin", "admin"));
        us.addUser(new User(3, "Super Admin", "superadmin@gmail.com", "super123", "super_admin"));

        User loggedUser = null;
        int authChoice;

        // ==================== AUTHENTICATION FLOW ====================
        do {
            System.out.println("\n╔════════════════════════════════════╗");
            System.out.println("║   CAMPUS EVENT MANAGEMENT SYSTEM   ║");
            System.out.println("╚════════════════════════════════════╝");
            System.out.println("\n===== AUTHENTICATION MENU =====");
            System.out.println("1. Login");
            System.out.println("2. Sign Up");
            System.out.println("3. Exit");

            System.out.print("\nEnter choice: ");
            authChoice = readInt(sc);
            if (authChoice == -1) return; // EOF handled

            switch (authChoice) {
                case 1:
                    loggedUser = handleLogin(sc, as, us, auditService);
                    break;

                case 2:
                    handleSignup(sc, as, us, auditService);
                    break;

                case 3:
                    System.out.println("\n✓ Thank you for using the system. Goodbye!");
                    sc.close();
                    return;

                default:
                    System.out.println("✗ Invalid choice! Please try again.");
            }

        } while (loggedUser == null && authChoice != 3);

        // If no user logged in, exit
        if (loggedUser == null) {
            sc.close();
            return;
        }

        // ==================== MAIN APPLICATION MENU ====================
        auditService.logAction(loggedUser.getEmail(), "LOGIN_SUCCESS", "Role: " + loggedUser.getRole());
        System.out.println("\n✓ Login Successful! Role: " + loggedUser.getRole());

        int choice;

        do {
            System.out.println("\n===== MENU =====");
            if (loggedUser.getRole().equals("admin")) {
                System.out.println("1. View MY Events (Only Mine)");
                System.out.println("2. Create Event");
                System.out.println("3. View MY Event Registrations (Only Mine)");
                System.out.println("4. Delete/Archive Event");
                System.out.println("5. Logout");
                System.out.println("9. Manual Student Check-in");
                System.out.println("10. View Event Insights");
                System.out.println("11. View Event History");
                System.out.println("12. View Event Feedback");
                System.out.println("--- VOLUNTEER MANAGEMENT ---");
                System.out.println("13. Setup Volunteer Policy for Event");
                System.out.println("14. Create Volunteer Team");
                System.out.println("15. View/Approve Volunteer Applications");
                System.out.println("16. Assign Task to Volunteers");
                System.out.println("17. Volunteer Check-in (Attendance)");
                System.out.println("18. View Volunteer Report");
                System.out.println("0. Exit");
            } else if (loggedUser.getRole().equals("super_admin")) {
                System.out.println("1. View Events");
                System.out.println("4. Manage User Role/Status (Super Admin)");
                System.out.println("5. View All Users (Super Admin Manager)");
                System.out.println("6. View Audit Logs (Super Admin Manager)");
                System.out.println("7. Delete/Archive Event");
                System.out.println("9. Manual Student Check-in");
                System.out.println("10. View Event Insights");
                System.out.println("11. View Event History");
                System.out.println("12. View Event Feedback");
                System.out.println("--- CLUB & VOLUNTEER ---");
                System.out.println("13. Manage Clubs (Create/View)");
                System.out.println("14. Add Member to Club");
                System.out.println("18. View Volunteer Report");
                System.out.println("0. Exit");
            } else {
                System.out.println("1. View Events");
                System.out.println("2. Search Events by Category");
                System.out.println("3. Register for Event (Student)");
                System.out.println("4. Submit Event Feedback");
                System.out.println("--- VOLUNTEER ---");
                System.out.println("13. Apply as Volunteer");
                System.out.println("14. View My Volunteer Applications");
                System.out.println("15. View My Tasks");
                System.out.println("16. Update Task Status");
                System.out.println("17. Submit Activity Log");
                System.out.println("18. Check-in/Check-out Attendance");
                System.out.println("19. Check Certificate Eligibility");
                System.out.println("0. Exit");
            }

            System.out.print("Enter choice: ");
            choice = readInt(sc);
            if (choice == -1) {
                System.out.println("\nâš ï¸  Input stream interrupted. Exiting gracefully...");
                choice = 0;
                break;
            }

            switch (choice) {

                case 1:
                    if (loggedUser.getRole().equals("admin")) {
                        es.viewMyEvents(loggedUser.getId());
                    } else if (loggedUser.getRole().equals("super_admin")) {
                        manager.viewAllEvents(loggedUser);
                    } else {
                        es.viewEvents();
                    }
                    break;
                case 2:
                    if (loggedUser.getRole().equals("admin")) {
                        System.out.print("Enter Event Title: ");
                        String title = sc.nextLine();

                        System.out.print("Enter Venue: ");
                        String venue = sc.nextLine();

                        System.out.print("Enter Capacity: ");
                        int capacity = readInt(sc);

                        System.out.println("\nSELECT CATEGORY:");
                        java.util.List<Category> cats = cs.getAllCategories();
                        for (Category c : cats) {
                            System.out.println(c.getId() + ". " + c.getName());
                        }
                        System.out.print("Enter Category ID: ");
                        int catId = readInt(sc);

                        System.out.print("Enter Event Date (YYYY-MM-DD HH:MM:SS): ");
                        String dateStr = sc.nextLine();
                        java.sql.Timestamp eventDate;
                        try {
                            eventDate = java.sql.Timestamp.valueOf(dateStr);
                        } catch (Exception ex) {
                            System.out.println("Invalid date format. Using current time.");
                            eventDate = new java.sql.Timestamp(System.currentTimeMillis());
                        }

                        Event event = new Event(0, title, venue, capacity, loggedUser.getId(), catId, eventDate);
                        es.createEvent(event, loggedUser.getId(), us);
                        auditService.logAction(loggedUser.getEmail(), "CREATE_EVENT",
                                "Title: " + title + ", Cat: " + catId + ", Date: " + eventDate);

                        System.out.println("Event Created!");
                    } else if (loggedUser.getRole().equals("student")) {
                        System.out.println("\n===== SEARCH BY CATEGORY =====");
                        java.util.List<Category> allCats = cs.getAllCategories();
                        for (Category c : allCats) {
                            System.out.println(c.getId() + ". " + c.getName());
                        }
                        System.out.print("Enter Category ID to filter: ");
                        int filterCatId = readInt(sc);

                        java.util.List<Event> filtered = searchService.getEvents(filterCatId, null, null);
                        System.out.println("\n--- Results ---");
                        if (filtered.isEmpty()) {
                            System.out.println("No events found in this category.");
                        } else {
                            for (Event ev : filtered) {
                                System.out.println("[" + ev.getId() + "] " + ev.getTitle() + " | " + ev.getVenue()
                                        + " | Available Seats: " + ev.getAvailableSeats() + "/" + ev.getCapacity());
                            }
                        }
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 3:
                    if (loggedUser.getRole().equals("admin")) {
                        es.viewMyEventRegistrations(loggedUser.getId());
                    } else if (loggedUser.getRole().equals("student")) {
                        System.out.print("Enter Event ID: ");
                        int eventId = sc.nextInt();

                        boolean registered = rs.register(loggedUser.getId(), eventId);
                        if (registered) {
                            auditService.logAction(loggedUser.getEmail(), "REGISTER_EVENT", "Event ID: " + eventId);
                        }
                    } else {
                        System.out.println("Only students can register!");
                    }
                    break;

                case 4:
                    if (loggedUser.getRole().equals("admin")) {
                        System.out.print("Enter Event ID to archive: ");
                        int eventIdToArchive = sc.nextInt();
                        sc.nextLine();
                        try {
                            manager.deleteEvent(loggedUser, eventIdToArchive);
                            auditService.logAction(loggedUser.getEmail(), "ARCHIVE_EVENT",
                                    "Event ID: " + eventIdToArchive);
                        } catch (SecurityException se) {
                            System.out.println(se.getMessage());
                        }
                    } else if (loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter User Email to Manage: ");
                        String em = sc.nextLine();

                        User u = us.findByEmail(em);
                        if (u != null) {
                            System.out.println("1. Promote to Admin");
                            System.out.println("2. Demote to Student");
                            System.out.println("3. Deactivate User");
                            System.out.print("Choose action: ");
                            int adminAction = sc.nextInt();
                            sc.nextLine();

                            if (adminAction == 1) {
                                manager.promoteToAdmin(loggedUser, u);
                                auditService.logAction(loggedUser.getEmail(), "PROMOTE_USER",
                                        "Promoted to admin: " + u.getEmail());
                                System.out.println("User promoted to Admin!");
                            } else if (adminAction == 2) {
                                manager.demoteToStudent(loggedUser, u);
                                auditService.logAction(loggedUser.getEmail(), "DEMOTE_USER",
                                        "Demoted to student: " + u.getEmail());
                                System.out.println("User demoted to Student!");
                            } else if (adminAction == 3) {
                                manager.deactivateUser(loggedUser, u);
                                auditService.logAction(loggedUser.getEmail(), "DEACTIVATE_USER",
                                        "Deactivated user: " + u.getEmail());
                                System.out.println("User deactivated!");
                            } else {
                                System.out.println("Invalid action!");
                            }
                        } else {
                            System.out.println("User not found.");
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        System.out.print("Enter Event ID: ");
                        int fbEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Rate the event (1-5): ");
                        int rating = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Leave a comment: ");
                        String comment = sc.nextLine();

                        service.FeedbackService feedbackService = new service.FeedbackService();
                        String submitMsg = feedbackService.submitFeedback(loggedUser.getId(), fbEventId, rating,
                                comment);
                        System.out.println(submitMsg);

                        if (submitMsg.contains("successfully")) {
                            auditService.logAction(loggedUser.getEmail(), "SUBMIT_FEEDBACK",
                                    "Event ID: " + fbEventId + " Rating: " + rating);
                        }
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 5:
                    if (loggedUser.getRole().equals("admin")) {
                        auditService.logAction(loggedUser.getEmail(), "LOGOUT", "Admin selected logout");
                        System.out.println("Exiting...");
                        choice = 0;
                    } else if (loggedUser.getRole().equals("super_admin")) {
                        manager.viewAllUsers(loggedUser, us);
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 6:
                    if (loggedUser.getRole().equals("super_admin")) {
                        manager.viewAuditLogs(loggedUser, auditService);
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 7:
                    if (loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter Event ID to archive: ");
                        int eventIdToArchive = sc.nextInt();
                        sc.nextLine();
                        try {
                            manager.deleteEvent(loggedUser, eventIdToArchive);
                            auditService.logAction(loggedUser.getEmail(), "ARCHIVE_EVENT",
                                    "Event ID: " + eventIdToArchive);
                        } catch (SecurityException se) {
                            System.out.println(se.getMessage());
                        }
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 9:
                    if (loggedUser.getRole().equals("admin") || loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter student email: ");
                        String checkinEmail = sc.nextLine();
                        System.out.print("Enter Event ID: ");
                        int checkinEventId = sc.nextInt();
                        sc.nextLine();
                        String checkinResult = service.QRService.manualCheckin(checkinEmail, checkinEventId, us);
                        System.out.println(checkinResult);
                        auditService.logAction(loggedUser.getEmail(), "MANUAL_CHECKIN",
                                "Student: " + checkinEmail + ", Event ID: " + checkinEventId);
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 10:
                    if (loggedUser.getRole().equals("admin") || loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter Event ID for insights: ");
                        int insightEventId = sc.nextInt();
                        sc.nextLine();

                        service.AnalyticsService analytics = new service.AnalyticsService();
                        service.FeedbackService feedback = new service.FeedbackService();

                        double fillRate = analytics.getFillRate(insightEventId);
                        double avgRating = feedback.getAverageRating(insightEventId);
                        int healthScore = analytics.calculateHealthScore(insightEventId);
                        String badge = analytics.assignBadge(healthScore);
                        int noShows = analytics.getNoShowCount(insightEventId);
                        String tip = analytics.getImprovementTip(fillRate, avgRating, noShows);

                        System.out.println("\n╔═══════════════════════════════════════════╗");
                        System.out.println("║           EVENT INSIGHTS                  ║");
                        System.out.println("╚═══════════════════════════════════════════╝");
                        System.out.printf("  Fill Rate:      %.1f%%\n", fillRate);
                        System.out.printf("  Avg Rating:     %.1f / 5.0\n", avgRating);
                        System.out.println("  Health Score:   " + healthScore + " / 100");
                        System.out.println("  Badge:          " + badge);
                        System.out.println("  No-Shows:       " + noShows);
                        System.out.println("  Tip:            " + tip);
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 11:
                    if (loggedUser.getRole().equals("admin") || loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter event title keyword: ");
                        String keyword = sc.nextLine();

                        AnalyticsService histAnalytics = new AnalyticsService();
                        java.util.List<String[]> history = histAnalytics.getEventHistory(keyword);

                        if (history.isEmpty()) {
                            System.out.println("No events found matching '" + keyword + "'.");
                        } else {
                            System.out.println(
                                    "\n╔═══════════════════════════════════════════════════════════════════════════════════╗");
                            System.out.println(
                                    "║                          EVENT HISTORY (Year-on-Year)                           ║");
                            System.out.println(
                                    "╚═══════════════════════════════════════════════════════════════════════════════════╝");
                            System.out.printf("%-15s %-12s %-6s %-8s %-8s %-8s %-7s %-8s %-7s\n",
                                    "Title", "Category", "Year", "Cap", "Reg", "Fill%", "Rating", "Reviews", "NoShow");
                            System.out.println("─".repeat(85));
                            for (String[] row : history) {
                                System.out.printf("%-15s %-12s %-6s %-8s %-8s %-8s %-7s %-8s %-7s\n",
                                        row[0].length() > 14 ? row[0].substring(0, 14) : row[0],
                                        row[1].length() > 11 ? row[1].substring(0, 11) : row[1],
                                        row[2], row[3], row[4], row[5], row[6], row[7], row[8]);
                            }
                        }
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                case 12:
                    if (loggedUser.getRole().equals("admin") || loggedUser.getRole().equals("super_admin")) {
                        System.out.print("Enter Event ID to view feedback: ");
                        int fbEventId = sc.nextInt();
                        sc.nextLine();

                        FeedbackService fbService = new FeedbackService();
                        java.util.List<Feedback> fbList = fbService.getFeedbackByEvent(fbEventId);

                        if (fbList.isEmpty()) {
                            System.out.println("No feedback found for Event " + fbEventId + ".");
                        } else {
                            System.out.println("\n===== FEEDBACK FOR EVENT " + fbEventId + " =====");
                            for (Feedback f : fbList) {
                                System.out.println("Rating: " + f.getRating() + "/5 | By User ID: " + f.getUserId());
                                System.out.println("Comment: " + f.getComment());
                                System.out.println("Date: " + f.getCreatedAt());
                                System.out.println("---------------------------------");
                            }
                        }
                    } else {
                        System.out.println("Access Denied!");
                    }
                    break;

                // ==================== VOLUNTEER MANAGEMENT CASES ====================

                case 13:
                    if (loggedUser.getRole().equals("admin")) {
                        // Setup Volunteer Policy
                        System.out.print("Enter Event ID: ");
                        int vpEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.println("Policy Type: 1=OPEN, 2=CLUB_ONLY, 3=MIXED");
                        System.out.print("Choose: ");
                        int pt = sc.nextInt();
                        sc.nextLine();
                        String policyType = pt == 2 ? "CLUB_ONLY" : pt == 3 ? "MIXED" : "OPEN";
                        System.out.println("Application Mode: 1=INDIVIDUAL, 2=TEAM_BASED, 3=MIXED");
                        System.out.print("Choose: ");
                        int am = sc.nextInt();
                        sc.nextLine();
                        String appMode = am == 2 ? "TEAM_BASED" : am == 3 ? "MIXED" : "INDIVIDUAL";
                        System.out.print("Max Volunteers: ");
                        int maxVol = sc.nextInt();
                        sc.nextLine();
                        int clubIdVol = 0;
                        if (pt == 2 || pt == 3) {
                            System.out.println("Available Clubs:");
                            for (Club c : clubService.getAllClubs()) {
                                System.out.println("  [" + c.getId() + "] " + c.getName());
                            }
                            System.out.print("Enter Club ID: ");
                            clubIdVol = sc.nextInt();
                            sc.nextLine();
                        }
                        System.out.println(
                                volunteerService.createPolicy(vpEventId, policyType, appMode, maxVol, clubIdVol));
                    } else if (loggedUser.getRole().equals("super_admin")) {
                        // Manage Clubs
                        System.out.println("1. Create Club  2. View All Clubs");
                        System.out.print("Choose: ");
                        int clubAction = sc.nextInt();
                        sc.nextLine();
                        if (clubAction == 1) {
                            System.out.print("Club Name: ");
                            String cName = sc.nextLine();
                            System.out.print("Description: ");
                            String cDesc = sc.nextLine();
                            System.out.println(clubService.createClub(cName, cDesc, loggedUser.getId()));
                        } else {
                            System.out.println("\n===== ALL CLUBS =====");
                            for (Club c : clubService.getAllClubs()) {
                                System.out.println("[" + c.getId() + "] " + c.getName() + " - " + c.getDescription());
                            }
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        // Apply as Volunteer
                        System.out.print("Enter Event ID: ");
                        int vaEventId = sc.nextInt();
                        sc.nextLine();
                        java.util.List<VolunteerTeam> teams = volunteerService.getTeams(vaEventId);
                        if (teams.isEmpty()) {
                            System.out.println("No volunteer teams for this event.");
                            break;
                        }
                        System.out.println("Available Teams:");
                        for (VolunteerTeam t : teams) {
                            System.out.println(
                                    "  [" + t.getId() + "] " + t.getTeamName() + " (max: " + t.getMaxMembers() + ")");
                        }
                        System.out.print("Enter Team ID: ");
                        int vaTeamId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Note (optional): ");
                        String vaNote = sc.nextLine();
                        System.out.println(
                                volunteerService.applyIndividual(vaEventId, loggedUser.getId(), vaTeamId, vaNote));
                    }
                    break;

                case 14:
                    if (loggedUser.getRole().equals("admin")) {
                        // Create Volunteer Team
                        System.out.print("Enter Event ID: ");
                        int vtEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Team Name (e.g. Tech, Cultural, Decor): ");
                        String vtName = sc.nextLine();
                        System.out.print("Max Members: ");
                        int vtMax = sc.nextInt();
                        sc.nextLine();
                        System.out.println(volunteerService.createTeam(vtEventId, vtName, vtMax));
                    } else if (loggedUser.getRole().equals("super_admin")) {
                        // Add Member to Club
                        System.out.print("Enter Club ID: ");
                        int cmClubId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Enter Student Email: ");
                        String cmEmail = sc.nextLine();
                        User cmUser = us.findByEmail(cmEmail);
                        if (cmUser != null) {
                            System.out.println(clubService.addMember(cmClubId, cmUser.getId()));
                        } else {
                            System.out.println("User not found.");
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        // View My Applications
                        java.util.List<VolunteerApplication> myApps = volunteerService
                                .getMyApplications(loggedUser.getId());
                        if (myApps.isEmpty()) {
                            System.out.println("No volunteer applications found.");
                            break;
                        }
                        System.out.println("\n===== MY VOLUNTEER APPLICATIONS =====");
                        for (VolunteerApplication a : myApps) {
                            System.out.println("[" + a.getId() + "] Event: " + a.getEventId() +
                                    " | Team: " + a.getTeamId() + " | Status: " + a.getStatus());
                        }
                    }
                    break;

                case 15:
                    if (loggedUser.getRole().equals("admin")) {
                        // View/Approve Applications
                        System.out.print("Enter Event ID: ");
                        int apEventId = sc.nextInt();
                        sc.nextLine();
                        java.util.List<VolunteerApplication> apps = volunteerService.getApplications(apEventId);
                        if (apps.isEmpty()) {
                            System.out.println("No applications found.");
                            break;
                        }
                        for (VolunteerApplication a : apps) {
                            System.out.println("[" + a.getId() + "] User: " + a.getUserId() +
                                    " | Type: " + a.getApplicationType() + " | Status: " + a.getStatus());
                        }
                        System.out.print("Enter Application ID to review (0 to skip): ");
                        int appId = sc.nextInt();
                        sc.nextLine();
                        if (appId > 0) {
                            System.out.print("APPROVED or REJECTED: ");
                            String appStatus = sc.nextLine();
                            System.out.println(volunteerService.reviewApplication(appId, appStatus));
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        // View My Tasks
                        java.util.List<VolunteerTask> myTasks = taskService.getTasksByUser(loggedUser.getId());
                        if (myTasks.isEmpty()) {
                            System.out.println("No tasks assigned.");
                            break;
                        }
                        System.out.println("\n===== MY VOLUNTEER TASKS =====");
                        for (model.VolunteerTask t : myTasks) {
                            System.out.println("[" + t.getId() + "] " + t.getTitle() +
                                    " | Status: " + t.getStatus() + " | Priority: " + t.getPriority() +
                                    " | Due: " + (t.getDueDate() != null ? t.getDueDate() : "N/A"));
                        }
                    }
                    break;

                case 16:
                    if (loggedUser.getRole().equals("admin")) {
                        // Assign Task
                        System.out.print("Enter Event ID: ");
                        int tkEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Task Title: ");
                        String tkTitle = sc.nextLine();
                        System.out.print("Description: ");
                        String tkDesc = sc.nextLine();
                        System.out.print("Priority (LOW/MEDIUM/HIGH): ");
                        String tkPri = sc.nextLine();
                        System.out.print("Due Date (YYYY-MM-DD HH:MM:SS or empty): ");
                        String tkDue = sc.nextLine();
                        System.out.println("Assign to: 1=Team, 2=Individual");
                        int assignType = sc.nextInt();
                        sc.nextLine();
                        int tkTeamId = 0, tkUserId = 0;
                        if (assignType == 1) {
                            System.out.print("Team ID: ");
                            tkTeamId = sc.nextInt();
                            sc.nextLine();
                        } else {
                            System.out.print("User ID: ");
                            tkUserId = sc.nextInt();
                            sc.nextLine();
                        }
                        System.out.println(taskService.createTask(tkEventId, tkTeamId, tkUserId, tkTitle, tkDesc, tkPri,
                                tkDue.isEmpty() ? null : tkDue, loggedUser.getId()));
                    } else if (loggedUser.getRole().equals("student")) {
                        // Update Task Status
                        System.out.print("Enter Task ID: ");
                        int tsId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("New Status (TODO/IN_PROGRESS/DONE): ");
                        String tsStatus = sc.nextLine();
                        System.out.println(taskService.updateTaskStatus(tsId, loggedUser.getId(), tsStatus));
                    }
                    break;

                case 17:
                    if (loggedUser.getRole().equals("admin")) {
                        // Volunteer Check-in
                        System.out.print("Enter Event ID: ");
                        int ciEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Enter Volunteer Email: ");
                        String ciEmail = sc.nextLine();
                        model.User ciUser = us.findByEmail(ciEmail);
                        if (ciUser != null) {
                            System.out.println(attendanceService.checkIn(ciEventId, ciUser.getId(), "MANUAL"));
                        } else {
                            System.out.println("User not found.");
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        // Submit Activity Log
                        System.out.print("Enter Task ID: ");
                        int alTaskId = sc.nextInt();
                        sc.nextLine();
                        System.out.print("Work Description: ");
                        String alText = sc.nextLine();
                        System.out.print("Hours Spent: ");
                        double alHours = sc.nextDouble();
                        sc.nextLine();
                        System.out
                                .println(taskService.submitActivityLog(alTaskId, loggedUser.getId(), alText, alHours));
                    }
                    break;

                case 18:
                    if (loggedUser.getRole().equals("admin") || loggedUser.getRole().equals("super_admin")) {
                        // View Volunteer Report
                        System.out.print("Enter Event ID: ");
                        int rpEventId = sc.nextInt();
                        sc.nextLine();
                        java.util.HashMap<String, String> summary = reportService.getEventSummary(rpEventId);
                        System.out.println("\n╔═══════════════════════════════════════════╗");
                        System.out.println("║       VOLUNTEER EVENT REPORT              ║");
                        System.out.println("╚═══════════════════════════════════════════╝");
                        System.out.println("  Total Volunteers:  " + summary.getOrDefault("totalVolunteers", "0"));
                        System.out.println("  Total Tasks:       " + summary.getOrDefault("totalTasks", "0"));
                        System.out.println("  Completed Tasks:   " + summary.getOrDefault("doneTasks", "0"));
                        System.out.println("  Total Check-ins:   " + summary.getOrDefault("totalCheckins", "0"));
                        System.out.println("  Hours Logged:      " + summary.getOrDefault("totalHoursLogged", "0"));

                        // Task progress bar
                        int[] progress = taskService.getTaskProgress(rpEventId);
                        if (progress[0] > 0) {
                            int pct = (progress[1] * 100) / progress[0];
                            System.out.println(
                                    "  Progress:          " + pct + "% (" + progress[1] + "/" + progress[0] + " done)");
                        }
                    } else if (loggedUser.getRole().equals("student")) {
                        // Check-in / Check-out
                        System.out.print("Enter Event ID: ");
                        int stAttEventId = sc.nextInt();
                        sc.nextLine();
                        System.out.println("1. Check-in  2. Check-out");
                        System.out.print("Choose: ");
                        int attAction = sc.nextInt();
                        sc.nextLine();
                        if (attAction == 1) {
                            System.out.println(attendanceService.checkIn(stAttEventId, loggedUser.getId(), "MANUAL"));
                        } else {
                            System.out.println(attendanceService.checkOut(stAttEventId, loggedUser.getId()));
                        }
                    }
                    break;

                case 19:
                    if (loggedUser.getRole().equals("student")) {
                        // Certificate Eligibility Check
                        System.out.print("Enter Event ID: ");
                        int certEventId = sc.nextInt();
                        sc.nextLine();
                        java.util.HashMap<String, String> cert = reportService.checkCertificateEligibility(certEventId,
                                loggedUser.getId());
                        System.out.println("\n╔═══════════════════════════════════════════╗");
                        System.out.println("║     CERTIFICATE ELIGIBILITY CHECK         ║");
                        System.out.println("╚═══════════════════════════════════════════╝");
                        System.out.println("  Attendance:   " + cert.getOrDefault("attendancePct", "0") + "%" +
                                " (" + cert.getOrDefault("daysPresent", "0") + "/"
                                + cert.getOrDefault("totalEventDays", "0") + " days)");
                        System.out.println("  Tasks:        " + cert.getOrDefault("doneTasks", "0") + "/"
                                + cert.getOrDefault("totalTasks", "0") + " completed");
                        String reward = cert.getOrDefault("reward", "NOT_ELIGIBLE");
                        if ("EXCELLENCE_AWARD".equals(reward)) {
                            System.out.println("  🏆 EXCELLENCE AWARD — Outstanding performance!");
                        } else if ("VOLUNTEER_CERTIFICATE".equals(reward)) {
                            System.out.println("  🏅 VOLUNTEER CERTIFICATE — Great work!");
                        } else {
                            System.out.println("  ❌ NOT ELIGIBLE — " + cert.getOrDefault("reason", ""));
                        }
                    } else {
                        System.out.println("Only students can check certificate eligibility.");
                    }
                    break;

                case 0:
                    auditService.logAction(loggedUser.getEmail(), "LOGOUT", "User exited from app menu");
                    System.out.println("Exiting...");
                    break;

                default:
                    System.out.println("Invalid choice!");
            }

        } while (choice != 0);

        sc.close();
    }

    // ==================== LOGIN HANDLER ====================
    private static User handleLogin(Scanner sc, Authservice as, Userservice us, AuditService auditService) {
        System.out.println("\n===== LOGIN FORM =====");
        System.out.print("Enter Email: ");
        String email = sc.nextLine();

        System.out.print("Enter Password: ");
        String password = sc.nextLine();

        User loggedUser = as.login(email, password, us);

        if (loggedUser == null) {
            System.out.println("✗ Invalid Login! Email or password is incorrect.");
            auditService.logAction(email, "LOGIN_FAILED", "Invalid credentials");
            return null;
        }

        return loggedUser;
    }

    // ==================== SIGNUP HANDLER ====================
    private static void handleSignup(Scanner sc, Authservice as, Userservice us, AuditService auditService) {
        System.out.println("\n===== SIGNUP FORM =====");

        System.out.print("Enter Full Name: ");
        String name = sc.nextLine();

        System.out.print("Enter Email: ");
        String email = sc.nextLine();

        // Validate email format first
        Authservice.EmailValidationResult emailVal = Authservice.validateEmail(email);
        if (!emailVal.isValid()) {
            System.out.println("✗ Email Validation Failed: " + emailVal.getMessage());
            auditService.logAction(email, "SIGNUP_FAILED", "Invalid email format");
            return;
        }

        System.out.println("\n📋 PASSWORD REQUIREMENTS:");
        System.out.println("  • Minimum 8 characters");
        System.out.println("  • At least 1 UPPERCASE letter (A-Z)");
        System.out.println("  • At least 1 lowercase letter (a-z)");
        System.out.println("  • At least 1 digit (0-9)");
        System.out.println("  • At least 1 special character (!@#$%^&* etc.)");
        System.out.println("  Example: MyPassword@123");

        System.out.print("\nEnter Password: ");
        String password = sc.nextLine();

        // Validate password strength
        Authservice.PasswordValidationResult passVal = Authservice.validatePassword(password);
        if (!passVal.isValid()) {
            System.out.println("✗ Password Validation Failed: " + passVal.getMessage());
            auditService.logAction(email, "SIGNUP_FAILED", "Weak password");
            return;
        }

        System.out.print("Confirm Password: ");
        String confirmPassword = sc.nextLine();

        if (!password.equals(confirmPassword)) {
            System.out.println("✗ Passwords do not match!");
            auditService.logAction(email, "SIGNUP_FAILED", "Password mismatch");
            return;
        }

        System.out.println("\nAvailable Roles: student, admin");
        System.out.print("Select Role (student/admin): ");
        String role = sc.nextLine();

        // Call signup method from Authservice
        Authservice.SignupResult result = as.signup(name, email, password, confirmPassword, role, us);

        if (result.isSuccess()) {
            System.out.println("✓ " + result.getMessage());
            auditService.logAction(email, "SIGNUP_SUCCESS", "Role: " + role);
        } else {
            System.out.println("✗ " + result.getMessage());
            auditService.logAction(email, "SIGNUP_FAILED", result.getMessage());
        }
    }

    // ==================== UTILS ====================
    private static int readInt(Scanner sc) {
        try {
            if (!sc.hasNextInt()) {
                return -1;
            }
            int val = sc.nextInt();
            if (sc.hasNextLine()) sc.nextLine();
            return val;
        } catch (Exception e) {
            if (sc.hasNextLine()) sc.nextLine();
            return -2; // Error
        }
    }
}
