package bd.sammalani.alumni.domain.event;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bd.sammalani.alumni.common.error.ApiException;
import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.config.CacheConfig;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;
    private final AppProperties props;

    /** The reunion this deployment is serving, with its ticket prices. */
    @Transactional(readOnly = true)
    public Event current() {
        return bySlug(props.event().defaultSlug());
    }

    @Transactional(readOnly = true)
    public Event bySlug(String slug) {
        return events.findBySlug(slug).orElseThrow(() -> ApiException.notFound(
                "No such event.", "এমন কোনো আয়োজন নেই।"));
    }

    /**
     * Read on every registration screen and changed only when the committee
     * meets, so it is cached by slug rather than fetched with its ticket rows
     * on each page load.
     */
    @Cacheable(value = CacheConfig.EVENT_BY_SLUG, key = "#a0")
    @Transactional(readOnly = true)
    public EventDto describe(String slug) {
        Event event = bySlug(slug);
        return new EventDto(
                event.getSlug(), event.getTitle(), event.getTitleBn(),
                event.getSubtitle(), event.getSubtitleBn(),
                event.getStartsAt(), event.getEndsAt(),
                event.getVenue(), event.getVenueBn(), event.getStatus(),
                event.getTicketTypes().stream()
                        .map(t -> new TicketTypeDto(t.getCode(), t.getName(), t.getNameBn(),
                                t.getNote(), t.getNoteBn(), t.getAmountBdt(), t.getRelation()))
                        .toList());
    }

    public record EventDto(String slug, String title, String titleBn, String subtitle, String subtitleBn,
                           java.time.Instant startsAt, java.time.Instant endsAt,
                           String venue, String venueBn, EventStatus status,
                           List<TicketTypeDto> ticketTypes) {
    }

    public record TicketTypeDto(String code, String name, String nameBn, String note, String noteBn,
                                java.math.BigDecimal amount,
                                bd.sammalani.alumni.domain.registration.GuestRelation relation) {
    }
}
