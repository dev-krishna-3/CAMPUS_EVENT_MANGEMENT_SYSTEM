/*
 * Decompiled with CFR 0.152.
 */
package model;

public class VolunteerAttendance {
    private int id;
    private int eventId;
    private int userId;
    private String checkInTime;
    private String checkOutTime;
    private String checkInMethod;
    private String attendanceDate;

    public VolunteerAttendance(int n, int n2, int n3, String string, String string2, String string3, String string4) {
        this.id = n;
        this.eventId = n2;
        this.userId = n3;
        this.checkInTime = string;
        this.checkOutTime = string2;
        this.checkInMethod = string3;
        this.attendanceDate = string4;
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

    public String getCheckInTime() {
        return this.checkInTime;
    }

    public String getCheckOutTime() {
        return this.checkOutTime;
    }

    public String getCheckInMethod() {
        return this.checkInMethod;
    }

    public String getAttendanceDate() {
        return this.attendanceDate;
    }
}
