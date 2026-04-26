package model;

public class VolunteerActivityLog {
    private int id;
    private int taskId;
    private int userId;
    private String logText;
    private double hoursSpent;
    private String createdAt;

    public VolunteerActivityLog(int id, int taskId, int userId,
                                 String logText, double hoursSpent, String createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.userId = userId;
        this.logText = logText;
        this.hoursSpent = hoursSpent;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getTaskId() { return taskId; }
    public int getUserId() { return userId; }
    public String getLogText() { return logText; }
    public double getHoursSpent() { return hoursSpent; }
    public String getCreatedAt() { return createdAt; }
}
