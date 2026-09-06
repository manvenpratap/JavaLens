package com.example;

public class MyClass {
    private String name;
    private int age;

    // START_MERGE
    private String email; // ADDED
    // END_MERGE

    public MyClass() {}

    // START_MERGE
    public void printInfo() {
        System.out.println(name + ": " + age + " (" + email + ")");
    }

    public String getEmail() {
        return email;
    }
    // END_MERGE
}
