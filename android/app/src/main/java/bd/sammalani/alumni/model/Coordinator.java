package bd.sammalani.alumni.model;

public class Coordinator {
    public String id;
    public String name;
    public String nameBn;
    public String phone;

    public String displayName() {
        return (nameBn != null && !nameBn.isEmpty()) ? nameBn : name;
    }
}
