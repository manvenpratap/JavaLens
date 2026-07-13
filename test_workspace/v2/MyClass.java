package com.example;

public class MyClass {
    private String name;
    private int age;
    private String email; // ADDED

    public MyClass() {}

    // START_MERGE
    public void printInfo() {
        System.out.println(name + ": " + age + " (" + email + ")");
    }
    // END_MERGE
}
