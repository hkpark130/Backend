package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Devices;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;

public interface DevicesRepository extends JpaRepository<Devices, String>, JpaSpecificationExecutor<Devices> {

    Long countByCategoryIdAndIsUsable(Categories category, boolean isUsable);

    @EntityGraph(attributePaths = {
        "manageDep",
        "categoryId",
        "projectId"
    })
    @Query("SELECT d FROM Devices d")
    List<Devices> findAllWithBasics();

    @Override
    @NonNull
    @EntityGraph(attributePaths = {
        "manageDep",
        "categoryId",
        "projectId"
    })
    Page<Devices> findAll(@Nullable Specification<Devices> spec, @NonNull Pageable pageable);

    @Query("SELECT d.id AS id, c.name AS categoryName, d.isUsable AS isUsable, d.status AS status "
        + "FROM Devices d LEFT JOIN d.categoryId c")
    List<DeviceCategorySummary> findCategorySummaries();

    @EntityGraph(attributePaths = {
        "approvalDetails",
        "approvalDetails.request",
        "approvalDetails.requestedProject",
        "approvalDetails.requestedDepartment",
        "deviceTags",
        "deviceTags.tag",
        "manageDep",
        "categoryId",
        "projectId"
    })
    @Query("SELECT d FROM Devices d")
    List<Devices> findAllWithDetails();

    @EntityGraph(attributePaths = {
        "approvalDetails",
        "approvalDetails.request",
        "approvalDetails.requestedProject",
        "approvalDetails.requestedDepartment",
        "deviceTags",
        "deviceTags.tag",
        "manageDep",
        "categoryId",
        "projectId"
    })
    @Query("SELECT d FROM Devices d WHERE d.id IN :ids")
    List<Devices> findAllWithDetailsByIdIn(@Param("ids") Collection<String> ids);

    @Query("SELECT DISTINCT d FROM Devices d "
        + "LEFT JOIN FETCH d.manageDep "
        + "LEFT JOIN FETCH d.categoryId "
        + "LEFT JOIN FETCH d.projectId "
    + "WHERE ((:userUuid IS NOT NULL AND d.userUuid = :userUuid) "
    + "   OR (:normalizedUsername IS NOT NULL AND LOWER(d.realUser) = :normalizedUsername))")
    List<Devices> findAllForUser(@Param("userUuid") java.util.UUID userUuid,
                 @Param("normalizedUsername") String normalizedUsername);

    @Query("SELECT DISTINCT d FROM Devices d "
        + "LEFT JOIN FETCH d.manageDep "
        + "LEFT JOIN FETCH d.categoryId "
        + "LEFT JOIN FETCH d.projectId "
    + "WHERE LOWER(d.realUser) IN :normalizedUsernames")
    List<Devices> findAllForUsernames(@Param("normalizedUsernames") Collection<String> normalizedUsernames);

    @Query("SELECT DISTINCT d FROM Devices d LEFT JOIN FETCH d.approvalDetails WHERE d.status <> :status")
    List<Devices> findAllWithApprovalsAndStatusNot(@Param("status") String status);

    @Query("SELECT DISTINCT d FROM Devices d LEFT JOIN FETCH d.approvalDetails WHERE d.status = :status")
    List<Devices> findAllWithApprovalsAndStatus(@Param("status") String status);

    @Query(value = "SELECT d FROM Devices d WHERE d.status <> :status")
    List<Devices> findByStatusNot(@Param("status") String status);

    List<Devices> findByIsUsableTrue();

    interface DeviceCategorySummary {
        String getId();

        String getCategoryName();

        Boolean getIsUsable();

        String getStatus();
    }
}
