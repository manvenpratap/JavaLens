package com.example;

public class UserAccount {
    @Id
    private long id;
    @NotNull
    private String username;
    @NotNull
    private String email;
    private boolean verified;
    @NotNull
    private String role;

    // START_MERGE
    @NotNull
    private String mfaSecret;
    private boolean mfaActive;
    private long lastLoginTimestamp;
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
        System.out.println("Account " + username + " marked verified. MFA Status: " + (mfaActive ? "ENABLED" : "DISABLED"));
    }

    public void enableMfa(String secret) {
        this.mfaSecret = secret;
        this.mfaActive = true;
        System.out.println("Multi-factor authentication activated for user: " + this.username);
    }

    public boolean validateMfaCode(String inputCode) {
        return this.mfaActive && inputCode != null && !inputCode.isEmpty();
    }

    public void recordLogin() {
        this.lastLoginTimestamp = System.currentTimeMillis();
    }

    public boolean isMfaActive() {
        return this.mfaActive;
    }

    public long getLastLoginTimestamp() {
        return this.lastLoginTimestamp;
    }
    // END_MERGE

    public String getDisplayName() {
        return this.username + " <" + this.email + ">";
    }
}
