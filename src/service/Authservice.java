package service;
import model.User;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class Authservice {
    
    // ==================== OTP STATE ====================
    private static final Map<String, String> otpStorage = new ConcurrentHashMap<>();
    private static final Map<String, Long> otpTimestamps = new ConcurrentHashMap<>();
    private static final long OTP_VALIDITY_MS = 15 * 60 * 1000; // 15 minutes
    
    // ==================== PASSWORD VALIDATION AGENT ====================
    // Validates password against all security requirements
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};:'\",.<>?/\\\\|`~]");
    
    /**
     * Validates password strength
     * Requirements:
     * - Minimum 8 characters
     * - At least 1 uppercase letter (A-Z)
     * - At least 1 lowercase letter (a-z)
     * - At least 1 digit (0-9)
     * - At least 1 special character (!@#$%^&* etc.)
     */
    public static PasswordValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return new PasswordValidationResult(false, "Password cannot be empty");
        }
        
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return new PasswordValidationResult(false, 
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least 1 uppercase letter (A-Z)");
        }
        
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least 1 lowercase letter (a-z)");
        }
        
        if (!DIGIT_PATTERN.matcher(password).find()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least 1 digit (0-9)");
        }
        
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            return new PasswordValidationResult(false, 
                "Password must contain at least 1 special character (!@#$%^&* etc.)");
        }
        
        return new PasswordValidationResult(true, "Password is strong");
    }
    
    // ==================== EMAIL VALIDATION AGENT ====================
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    /**
     * Validates email format
     */
    public static EmailValidationResult validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            return new EmailValidationResult(false, "Email cannot be empty");
        }
        
        if (!EMAIL_PATTERN.matcher(email).find()) {
            return new EmailValidationResult(false, "Invalid email format");
        }
        
        return new EmailValidationResult(true, "Email is valid");
    }
    
    // ==================== LOGIN AGENT ====================
    /**
     * Login with existing credentials
     */
    public User login(String email, String password, Userservice userservice){
        User ur = userservice.findByEmail(email);
        if(ur != null && ur.getPassword().equals(password)){
            // Trigger login notification
            String subject = "Login Successful";
            String message = "Hello " + ur.getName() + ",\n\n" +
                             "You have successfully logged into the Campus Event System.";
            
            NotificationManager.getInstance().sendNotification(ur.getEmail(), subject, message);
            
            return ur;
        }
        return null;
    }
    
    // ==================== SIGNUP AGENT ====================
    /**
     * Register new user with complete validation
     * Returns SignupResult with status and message
     */
    public SignupResult signup(String name, String email, String password, 
                               String confirmPassword, String role, Userservice userservice) {
        
        // Validate name
        if (name == null || name.trim().isEmpty()) {
            return new SignupResult(false, "Name cannot be empty");
        }
        
        if (name.length() < 3) {
            return new SignupResult(false, "Name must be at least 3 characters long");
        }
        
        if (name.length() > 100) {
            return new SignupResult(false, "Name cannot exceed 100 characters");
        }
        
        // Validate email
        EmailValidationResult emailVal = validateEmail(email);
        if (!emailVal.isValid()) {
            return new SignupResult(false, "Email validation failed: " + emailVal.getMessage());
        }
        
        // Check if email already exists
        User existingUser = userservice.findByEmail(email);
        if (existingUser != null) {
            return new SignupResult(false, "Email already registered. Please use different email or login.");
        }
        
        // Validate password
        PasswordValidationResult passVal = validatePassword(password);
        if (!passVal.isValid()) {
            return new SignupResult(false, "Password validation failed: " + passVal.getMessage());
        }
        
        // Validate password confirmation
        if (!password.equals(confirmPassword)) {
            return new SignupResult(false, "Passwords do not match");
        }
        
        // Validate role
        if (role == null || role.trim().isEmpty()) {
            return new SignupResult(false, "Role cannot be empty");
        }
        
        // Only allow student and admin signup (super_admin must be created by system)
        if (!role.equalsIgnoreCase("student") && !role.equalsIgnoreCase("admin")) {
            return new SignupResult(false, "Invalid role. Allowed: student, admin");
        }
        
        // Create new user and add to database
        try {
            User newUser = new User(0, name, email, password, role.toLowerCase());
            userservice.addUser(newUser);
            return new SignupResult(true, "Signup successful! You can now login with your credentials.");
        } catch (Exception e) {
            return new SignupResult(false, "Signup failed: " + e.getMessage());
        }
    }
    
    // ==================== PASSWORD RESET AGENT ====================
    public SignupResult requestPasswordReset(String email, Userservice userservice, EmailService emailService) {
        EmailValidationResult emailVal = validateEmail(email);
        if (!emailVal.isValid()) {
            return new SignupResult(false, "Invalid email format");
        }
        
        User user = userservice.findByEmail(email);
        if (user == null) {
            return new SignupResult(false, "Email not found in our system");
        }
        
        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        otpStorage.put(email, otp);
        otpTimestamps.put(email, System.currentTimeMillis());
        
        String subject = "Password Reset Request";
        String body = "Hello " + user.getName() + ",\n\n" +
                      "Your OTP for password reset is: " + otp + "\n" +
                      "This OTP is valid for 15 minutes.\n\n" +
                      "If you did not request this, please ignore this email.";
                      
        boolean emailSent = emailService.sendEmail(email, subject, body);
        if (emailSent) {
            return new SignupResult(true, "OTP sent to your email successfully.");
        } else {
            return new SignupResult(false, "Failed to send OTP email. Please try again later.");
        }
    }

    public SignupResult resetPassword(String email, String otp, String newPassword, Userservice userservice) {
        if (!otpStorage.containsKey(email) || !otpTimestamps.containsKey(email)) {
            return new SignupResult(false, "No password reset requested for this email");
        }
        
        long timestamp = otpTimestamps.get(email);
        if (System.currentTimeMillis() - timestamp > OTP_VALIDITY_MS) {
            otpStorage.remove(email);
            otpTimestamps.remove(email);
            return new SignupResult(false, "OTP has expired. Please request a new one.");
        }
        
        if (!otpStorage.get(email).equals(otp)) {
            return new SignupResult(false, "Invalid OTP");
        }
        
        PasswordValidationResult passVal = validatePassword(newPassword);
        if (!passVal.isValid()) {
            return new SignupResult(false, passVal.getMessage());
        }
        
        boolean updated = userservice.updatePassword(email, newPassword);
        if (updated) {
            otpStorage.remove(email);
            otpTimestamps.remove(email);
            return new SignupResult(true, "Password has been successfully reset. You can now login.");
        } else {
            return new SignupResult(false, "Failed to update password. Please try again.");
        }
    }

    // ==================== NESTED RESULT CLASSES ====================
    
    /**
     * Result class for password validation
     */
    public static class PasswordValidationResult {
        private final boolean isValid;
        private final String message;
        
        public PasswordValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
        
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
    }
    
    /**
     * Result class for email validation
     */
    public static class EmailValidationResult {
        private final boolean isValid;
        private final String message;
        
        public EmailValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
        
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
    }
    
    /**
     * Result class for signup operation
     */
    public static class SignupResult {
        private final boolean success;
        private final String message;
        
        public SignupResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
