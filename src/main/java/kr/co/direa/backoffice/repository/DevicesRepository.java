package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Devices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevicesRepository extends JpaRepository<Devices, String> {

    Long countByCategoryIdAndIsUsable(Categories category, boolean isUsable);

    @Query("SELECT DISTINCT d FROM Devices d LEFT JOIN FETCH d.approvalDetails")
    List<Devices> findAllWithApprovals();

    @Query("SELECT DISTINCT d FROM Devices d "
        + "LEFT JOIN FETCH d.deviceTags tags "
        + "LEFT JOIN FETCH tags.tag "
        + "LEFT JOIN FETCH d.manageDep "
        + "LEFT JOIN FETCH d.categoryId "
        + "LEFT JOIN FETCH d.projectId "
        + "WHERE ((:userUuid IS NOT NULL AND d.userUuid = :userUuid) "
        + "   OR (:userUuid IS NOT NULL AND d.userId.externalId = :userUuid) "
        + "   OR (:normalizedUsername IS NOT NULL AND LOWER(d.realUser) = :normalizedUsername))")
    List<Devices> findAllForUser(@Param("userUuid") java.util.UUID userUuid,
                 @Param("normalizedUsername") String normalizedUsername);

    @Query("SELECT DISTINCT d FROM Devices d LEFT JOIN FETCH d.approvalDetails WHERE d.status <> :status")
    List<Devices> findAllWithApprovalsAndStatusNot(@Param("status") String status);

    @Query("SELECT DISTINCT d FROM Devices d LEFT JOIN FETCH d.approvalDetails WHERE d.status = :status")
    List<Devices> findAllWithApprovalsAndStatus(@Param("status") String status);

    @Query(value = "SELECT d FROM Devices d WHERE d.status <> :status")
    List<Devices> findByStatusNot(@Param("status") String status);

    List<Devices> findByIsUsableTrue();
}
