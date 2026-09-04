package bd.sammalani.alumni.model;

public class Person {
    public String id;
    public String name;
    public String nameBn;
    public int batchYear;
    public String status; // "SEEDED" | "CLAIMED" | "VERIFIED" | "REJECTED"
    public String phone;
    public String email;
    public String gender;
    public String dob;
    public String bloodGroup;
    public String occupation;
    public String city;
    public String photoUrl;
    public boolean deceased;

    public String displayName() {
        return (nameBn != null && !nameBn.isEmpty()) ? nameBn : name;
    }

    public String initials() {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return String.valueOf(parts[0].charAt(0)).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + String.valueOf(parts[parts.length - 1].charAt(0))).toUpperCase();
    }
}
