/*
 * Decompiled with CFR 0.152.
 */
package model;

public class VolunteerTeam {
    private int id;
    private int eventId;
    private String teamName;
    private int maxMembers;

    public VolunteerTeam(int n, int n2, String string, int n3) {
        this.id = n;
        this.eventId = n2;
        this.teamName = string;
        this.maxMembers = n3;
    }

    public int getId() {
        return this.id;
    }

    public int getEventId() {
        return this.eventId;
    }

    public String getTeamName() {
        return this.teamName;
    }

    public int getMaxMembers() {
        return this.maxMembers;
    }
}
