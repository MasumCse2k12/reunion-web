package bd.sammalani.alumni.domain.referral;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    boolean existsByPhoneAndBatchYear(String phone, Integer batchYear);
}
