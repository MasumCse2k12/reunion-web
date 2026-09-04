package bd.sammalani.alumni.model;

import java.util.List;

public class DashboardData {
    public Person me;
    public Batch batch;
    public EventInfo event;
    public Registration registration;
    public List<Notice> notices;
    public List<Person> missingFromBatch;
    public int totalRoster;
    public int totalClaimed;
    public int totalBatches;

    public int profileCompleteness() {
        if (me == null) return 0;
        int filled = 0;
        int total = 6;
        if (me.name != null && !me.name.isEmpty()) filled++;
        if (me.batchYear > 0) filled++;
        if (me.phone != null && !me.phone.isEmpty()) filled++;
        if (me.occupation != null && !me.occupation.isEmpty()) filled++;
        if (me.city != null && !me.city.isEmpty()) filled++;
        if (registration != null) filled++;
        return Math.round((filled * 100f) / total);
    }
}
