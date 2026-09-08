package com.example;

public class MyClass {
    private String name;
    private int age;

    // START_MERGE
    private String email;
    private String department;
    private String phoneNumber;
    // END_MERGE

    public MyClass() {}

    // START_MERGE
    public void printInfo() {
        System.out.println(name + " (" + age + ") | Dept: " + department + " | Email: " + email + " | Phone: " + phoneNumber);
    }

    public String getEmail() {
        return this.email;
    }

    public String getDepartment() {
        return this.department;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public void updateContactInfo(String email, String phoneNumber) {
        this.email = email;
        this.phoneNumber = phoneNumber;
    }
    // END_MERGE
}
