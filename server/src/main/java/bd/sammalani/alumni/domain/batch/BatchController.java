package bd.sammalani.alumni.domain.batch;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bd.sammalani.alumni.domain.batch.BatchService.BatchDto;
import bd.sammalani.alumni.domain.batch.BatchService.Totals;
import bd.sammalani.alumni.domain.person.PersonDto;
import bd.sammalani.alumni.domain.person.PersonRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Batches", description = "The 59 batches and how much of each has been found")
@RequiredArgsConstructor
public class BatchController {

    private static final int MAX_MEMBERS = 200;

    private final BatchService batchService;
    private final PersonRepository people;

    @GetMapping
    @Operation(summary = "Every batch with its coverage")
    public List<BatchDto> all() {
        return batchService.coverage();
    }

    @GetMapping("/totals")
    @Operation(summary = "Roster and claimed totals across all batches")
    public Totals totals() {
        return batchService.totals();
    }

    /**
     * Requires a member session — unlike the counters above, this is a list of
     * real people, so it is never public and every row is masked.
     */
    @GetMapping("/{year}/members")
    @Operation(summary = "Who is on a batch (masked)")
    public List<PersonDto> members(@PathVariable int year, @RequestParam(required = false) String q) {
        return people.searchInBatch(year, q, Limit.of(MAX_MEMBERS)).stream()
                .map(PersonDto::masked)
                .toList();
    }

    @GetMapping("/{year}/missing")
    @Operation(summary = "People on this batch nobody has claimed yet")
    public List<PersonDto> missing(@PathVariable int year) {
        return people.findMissingInBatch(year, Limit.of(50)).stream()
                .map(PersonDto::masked)
                .toList();
    }
}
