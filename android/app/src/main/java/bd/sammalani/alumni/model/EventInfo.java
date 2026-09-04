package bd.sammalani.alumni.model;

import java.util.List;

public class EventInfo {
    public String slug;
    public String title;
    public String titleBn;
    public String subtitle;
    public String subtitleBn;
    public String startsAt;
    public String endsAt;
    public String venue;
    public String venueBn;
    public String status;
    public List<TicketType> ticketTypes;

    public String displayTitle(boolean bn) {
        return (bn && titleBn != null && !titleBn.isEmpty()) ? titleBn : title;
    }

    public String displayVenue(boolean bn) {
        return (bn && venueBn != null && !venueBn.isEmpty()) ? venueBn : (venue != null ? venue : "");
    }

    public TicketType alumniTicket() {
        if (ticketTypes == null) return null;
        for (TicketType t : ticketTypes) {
            if ("ALUMNI".equals(t.code)) return t;
        }
        return null;
    }

    public TicketType ticketByCode(String code) {
        if (ticketTypes == null) return null;
        for (TicketType t : ticketTypes) {
            if (code.equals(t.code)) return t;
        }
        return null;
    }
}
