package org.zunr1;

public class User {
    @Column(name = "user_id")
    private Long id;
    @Column(name = "user_name")
    private String name;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
