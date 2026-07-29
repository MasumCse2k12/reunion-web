package bd.sammalani.alumni.domain.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bd.sammalani.alumni.common.jpa.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reunion. The platform is not scoped to one, which is why registrations hang
 * off an event rather than off a global "the reunion" singleton.
 */
@Entity
@Table(name = "event")
@Getter
@Setter
@NoArgsConstructor
public class Event extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_bn")
    private String titleBn;

    private String subtitle;

    @Column(name = "subtitle_bn")
    private String subtitleBn;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    private String venue;

    @Column(name = "venue_bn")
    private String venueBn;

    @Column(name = "venue_map_url")
    private String venueMapUrl;

    private Integer capacity;

    @Column(name = "reg_opens_at")
    private Instant regOpensAt;

    @Column(name = "reg_closes_at")
    private Instant regClosesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventStatus status = EventStatus.DRAFT;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<TicketType> ticketTypes = new ArrayList<>();

    public boolean acceptsRegistrations() {
        Instant now = Instant.now();
        return status == EventStatus.OPEN
                && (regOpensAt == null || !now.isBefore(regOpensAt))
                && (regClosesAt == null || now.isBefore(regClosesAt));
    }
}
