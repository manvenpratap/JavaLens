package com.example;

public class MyClass {
    private String name;
    private int age;

    // START_MERGE
    // END_MERGE

    public MyClass() {}

    // START_MERGE
    public void printInfo() {
        System.out.println(name + ": " + age);
    }
    // END_MERGE
}
