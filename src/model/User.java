package model;

public class User {
    private int id;
    private String name;
    private String email;
    private String password;
    private String role; /* student , admin , manager */

    public User(int id, String name, String email, String password, String role){
        this.id=id;
        this.name=name;
        this.email=email;
        this.password=password;
        this.role=role;
    }

    public int getId() { return id;}
    public String getName() {return name;}
    public String getEmail() {return email;}
    public String getPassword() { return password;}
    public String getRole() {return role;}
    public void setRole(String role) { this.role=role;}
}
