package bd.sammalani.alumni.domain.notice;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    List<Notice> findAllByOrderByPinnedDescPublishedAtDesc(Limit limit);
}
