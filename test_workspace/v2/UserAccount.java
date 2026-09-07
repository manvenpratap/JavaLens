package com.example;

public class UserAccount {
    private long id;
    private String username;
    private String email;
    private boolean verified;
    private String role;

    // START_MERGE
    private String mfaSecret; // ADDED
    private boolean mfaActive; // ADDED
    // END_MERGE

    public UserAccount(long id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.verified = false;
        this.role = "USER";
    }

    public long getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return this.email;
    }

    public boolean isVerified() {
        return this.verified;
    }

    // START_MERGE
    public void markVerified() {
        this.verified = true;
        System.out.println("Account " + username + " marked verified. MFA ready: " + mfaActive);
    }

    public void enableMfa(String secret) {
        this.mfaSecret = secret;
        this.mfaActive = true;
    }

    public boolean validateMfaCode(String inputCode) {
        return this.mfaActive && inputCode != null && !inputCode.isEmpty();
    }
    // END_MERGE

    public String getDisplayName() {
        return this.username + " <" + this.email + ">";
    }
}
