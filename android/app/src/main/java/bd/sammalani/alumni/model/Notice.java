package bd.sammalani.alumni.model;

public class Notice {
    public String id;
    public String title;
    public String titleBn;
    public String body;
    public String bodyBn;
    public boolean pinned;
    public String publishedAt;

    public String displayTitle(boolean bn) {
        return (bn && titleBn != null && !titleBn.isEmpty()) ? titleBn : title;
    }

    public String displayBody(boolean bn) {
        return (bn && bodyBn != null && !bodyBn.isEmpty()) ? bodyBn : body;
    }
}
