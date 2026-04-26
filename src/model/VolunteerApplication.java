/*
 * Decompiled with CFR 0.152.
 */
package model;

public class VolunteerApplication {
    private int id;
    private int eventId;
    private int userId;
    private int teamId;
    private String applicationType;
    private int teamLeaderId;
    private String status;
    private String note;
    private String appliedAt;
    private String reviewedAt;

    public VolunteerApplication(int n, int n2, int n3, int n4, String string, int n5, String string2, String string3, String string4, String string5) {
        this.id = n;
        this.eventId = n2;
        this.userId = n3;
        this.teamId = n4;
        this.applicationType = string;
        this.teamLeaderId = n5;
        this.status = string2;
        this.note = string3;
        this.appliedAt = string4;
        this.reviewedAt = string5;
    }

    public int getId() {
        return this.id;
    }

    public int getEventId() {
        return this.eventId;
    }

    public int getUserId() {
        return this.userId;
    }

    public int getTeamId() {
        return this.teamId;
    }

    public String getApplicationType() {
        return this.applicationType;
    }

    public int getTeamLeaderId() {
        return this.teamLeaderId;
    }

    public String getStatus() {
        return this.status;
    }

    public String getNote() {
        return this.note;
    }

    public String getAppliedAt() {
        return this.appliedAt;
    }

    public String getReviewedAt() {
        return this.reviewedAt;
    }
}
