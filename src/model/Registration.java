package model;

public class Registration {
    private int userid;
    private int eventid;

    public Registration(int userid, int eventid){
        this.userid=userid;
        this.eventid=eventid;
    }
    public int getUserId() { return userid;}
    public int getEventId() { return eventid;}
}
