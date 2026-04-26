package model;

public class AuditLog {
    private final int id;
    private final String actorEmail;
    private final String action;
    private final String details;
    private final String createdAt;

    public AuditLog(int id, String actorEmail, String action, String details, String createdAt) {
        this.id = id;
        this.actorEmail = actorEmail;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
