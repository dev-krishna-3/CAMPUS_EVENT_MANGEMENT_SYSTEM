/*
 * Decompiled with CFR 0.152.
 */
package model;

public class Club {
    private int id;
    private String name;
    private String description;
    private int createdBy;

    public Club(int n, String string, String string2, int n2) {
        this.id = n;
        this.name = string;
        this.description = string2;
        this.createdBy = n2;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public int getCreatedBy() {
        return this.createdBy;
    }
}
