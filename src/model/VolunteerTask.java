/*
 * Decompiled with CFR 0.152.
 */
package model;

public class VolunteerTask {
    private int id;
    private int eventId;
    private int teamId;
    private int assignedTo;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String dueDate;
    private int createdBy;
    private String createdAt;
    private String updatedAt;

    public VolunteerTask(int n, int n2, int n3, int n4, String string, String string2, String string3, String string4, String string5, int n5, String string6, String string7) {
        this.id = n;
        this.eventId = n2;
        this.teamId = n3;
        this.assignedTo = n4;
        this.title = string;
        this.description = string2;
        this.status = string3;
        this.priority = string4;
        this.dueDate = string5;
        this.createdBy = n5;
        this.createdAt = string6;
        this.updatedAt = string7;
    }

    public int getId() {
        return this.id;
    }

    public int getEventId() {
        return this.eventId;
    }

    public int getTeamId() {
        return this.teamId;
    }

    public int getAssignedTo() {
        return this.assignedTo;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getStatus() {
        return this.status;
    }

    public String getPriority() {
        return this.priority;
    }

    public String getDueDate() {
        return this.dueDate;
    }

    public int getCreatedBy() {
        return this.createdBy;
    }

    public String getCreatedAt() {
        return this.createdAt;
    }

    public String getUpdatedAt() {
        return this.updatedAt;
    }
}
