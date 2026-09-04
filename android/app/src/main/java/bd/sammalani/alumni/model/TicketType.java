package bd.sammalani.alumni.model;

public class TicketType {
    public String code;  // ALUMNI | SPOUSE | CHILD | CHILD_FREE | GUEST
    public String name;
    public String nameBn;
    public String note;
    public String noteBn;
    public double amount;
    public String relation;

    public String displayName(boolean bn) {
        return (bn && nameBn != null && !nameBn.isEmpty()) ? nameBn : name;
    }

    public String displayNote(boolean bn) {
        return (bn && noteBn != null && !noteBn.isEmpty()) ? noteBn : (note != null ? note : "");
    }
}
