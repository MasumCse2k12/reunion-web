package bd.sammalani.alumni.domain.notice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.config.CacheConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notices")
@Tag(name = "Notices", description = "The committee's notice board")
@SecurityRequirements
@RequiredArgsConstructor
class NoticeController {

    private final NoticeService service;

    @GetMapping
    @Operation(summary = "Latest notices, pinned first")
    List<NoticeDto> latest() {
        return service.latest();
    }
}

@Service
@RequiredArgsConstructor
class NoticeService {

    private static final int FEED_SIZE = 20;

    private final NoticeRepository notices;

    /** Read by everyone on every dashboard, written a few times a month. */
    @Cacheable(CacheConfig.NOTICES)
    @Transactional(readOnly = true)
    List<NoticeDto> latest() {
        return notices.findAllByOrderByPinnedDescPublishedAtDesc(Limit.of(FEED_SIZE)).stream()
                .map(NoticeDto::from)
                .toList();
    }
}
