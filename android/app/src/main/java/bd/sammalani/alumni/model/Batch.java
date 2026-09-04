package bd.sammalani.alumni.model;

public class Batch {
    public int year;
    public int rosterCount;
    public int claimedCount;

    public int percent() {
        if (rosterCount == 0) return 0;
        return Math.round((claimedCount * 100f) / rosterCount);
    }
}
