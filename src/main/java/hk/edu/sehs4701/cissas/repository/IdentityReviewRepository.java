package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.IdentityReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IdentityReviewRepository extends JpaRepository<IdentityReview, Long> {
    List<IdentityReview> findAllByCurrentMarker(Integer currentMarker);
    @Query("select r from IdentityReview r where r.currentMarker = 1 and r.samePerson = true "
            + "and (r.firstApplicant.id in :ids or r.secondApplicant.id in :ids)")
    List<IdentityReview> confirmedTouching(Collection<Long> ids);

    @Query("select r from IdentityReview r where r.currentMarker = 1 and "
            + "((r.firstApplicant.id = :first and r.secondApplicant.id = :second) "
            + "or (r.firstApplicant.id = :second and r.secondApplicant.id = :first))")
    Optional<IdentityReview> currentPair(Long first, Long second);

    @Query("select count(r) > 0 from IdentityReview r where r.currentMarker = 1 and r.samePerson = false and "
            + "((r.firstApplicant.id in :first and r.secondApplicant.id in :second) "
            + "or (r.firstApplicant.id in :second and r.secondApplicant.id in :first))")
    boolean hasDifferentDecision(Collection<Long> first, Collection<Long> second);
    @EntityGraph(attributePaths = {"firstApplication", "secondApplication", "reviewedBy"})
    @Query("select r from IdentityReview r where (:beforeId is null or r.id < :beforeId) order by r.id desc")
    Slice<IdentityReview> findHistory(Long beforeId, Pageable pageable);
}
