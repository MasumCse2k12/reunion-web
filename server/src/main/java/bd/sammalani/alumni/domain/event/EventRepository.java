package bd.sammalani.alumni.domain.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @EntityGraph(attributePaths = "ticketTypes")
    Optional<Event> findBySlug(String slug);
}
