package bd.sammalani.alumni.domain.batch;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BatchRepository extends JpaRepository<Batch, Integer> {

    List<Batch> findAllByOrderByYearAsc();

    /**
     * Coverage per batch in one pass: roster estimate against how many people on
     * that year have actually been claimed. Doing this as a projection keeps the
     * landing page off 59 separate count queries.
     */
    @Query("""
            select b.year           as year,
                   b.rosterEstimate as rosterCount,
                   sum(case when p.status in (bd.sammalani.alumni.domain.person.PersonStatus.CLAIMED,
                                              bd.sammalani.alumni.domain.person.PersonStatus.VERIFIED)
                            then 1 else 0 end) as claimedCount
            from Batch b
              left join Person p on p.batchYear = b.year and p.mergedIntoId is null
            group by b.year, b.rosterEstimate
            order by b.year
            """)
    List<BatchCoverage> coverage();

    interface BatchCoverage {
        int getYear();

        int getRosterCount();

        long getClaimedCount();
    }
}
