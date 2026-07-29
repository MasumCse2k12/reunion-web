package bd.sammalani.alumni.domain.notice;

import java.time.Instant;
import java.util.UUID;

public record NoticeDto(UUID id, String title, String titleBn, String body, String bodyBn,
                        boolean pinned, Instant publishedAt) {

    static NoticeDto from(Notice n) {
        return new NoticeDto(n.getId(), n.getTitle(), n.getTitleBn(), n.getBody(), n.getBodyBn(),
                n.isPinned(), n.getPublishedAt());
    }
}
