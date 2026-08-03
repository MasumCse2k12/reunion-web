package bd.sammalani.alumni.domain.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.config.AppProperties;
import bd.sammalani.alumni.domain.event.EventService.EventDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The reunion, and what a seat at it costs.
 * <p>
 * Public, and it has to be: the price is the first thing somebody asks about,
 * and they ask before they have proved a phone number. Nothing here identifies a
 * person — it is the poster, not the guest list.
 * <p>
 * This exists so that {@code TicketType}'s promise is actually kept. Prices live
 * in {@code ticket_type} precisely so the committee can change one without a
 * deployment, and the server has always priced registrations from that table —
 * but with no route to read it, the app had no way to learn a price and carried
 * its own copy. A change to the table moved the total a member owed and left
 * every line item on their screen showing the old number.
 */
@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Event", description = "The reunion and its ticket prices")
@RequiredArgsConstructor
public class EventController {

    private final EventService events;
    private final AppProperties props;

    @GetMapping("/current")
    @SecurityRequirements
    @Operation(summary = "The reunion this deployment serves, with its ticket prices",
            description = """
                    Ticket types come back in display order, each with the amount in taka. \
                    `relation` says which guest relation a ticket covers; null marks the \
                    member's own. Cached for an hour — prices change when the committee \
                    meets, not between page loads.""")
    public EventDto current() {
        return events.describe(props.event().defaultSlug());
    }

    @GetMapping("/{slug}")
    @SecurityRequirements
    @Operation(summary = "One reunion by slug",
            description = "The platform is not scoped to a single event; this is how an older one is read.")
    public EventDto bySlug(@PathVariable("slug") String slug) {
        return events.describe(slug);
    }
}
