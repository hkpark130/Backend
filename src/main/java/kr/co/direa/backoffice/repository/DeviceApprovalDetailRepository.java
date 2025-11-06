package kr.co.direa.backoffice.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;

public interface DeviceApprovalDetailRepository extends JpaRepository<DeviceApprovalDetail, Long> {

    @EntityGraph(attributePaths = {
        "request",
        "device",
        "requestedProject",
        "requestedDepartment"
    })
    @Query("SELECT dad FROM DeviceApprovalDetail dad " +
        "WHERE dad.device.id = :deviceId " +
        "ORDER BY dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findHistoryByDevice(@Param("deviceId") String deviceId);

    @EntityGraph(attributePaths = "request")
    @Query("SELECT dad FROM DeviceApprovalDetail dad " +
        "WHERE dad.device.id = :deviceId " +
        "AND dad.request.status IN :statuses " +
        "ORDER BY dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findActiveByDeviceIdAndStatuses(
        @Param("deviceId") String deviceId,
        @Param("statuses") List<ApprovalStatus> statuses);

    @EntityGraph(attributePaths = {
        "request",
        "device",
        "requestedProject",
        "requestedDepartment"
    })
    @Query("SELECT dad FROM DeviceApprovalDetail dad " +
        "WHERE dad.device.id IN :deviceIds " +
        "ORDER BY dad.device.id ASC, dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findHistoryByDeviceIds(@Param("deviceIds") Collection<String> deviceIds);

    @EntityGraph(attributePaths = {
        "request",
        "device"
    })
    List<DeviceApprovalDetail> findByAction(DeviceApprovalAction action);

    @EntityGraph(attributePaths = {
        "request",
        "device"
    })
    @Query("SELECT dad FROM DeviceApprovalDetail dad WHERE dad.device.id IN :deviceIds AND dad.action = :action")
    List<DeviceApprovalDetail> findByDeviceIdInAndAction(@Param("deviceIds") Collection<String> deviceIds,
                                                         @Param("action") DeviceApprovalAction action);

    @Query("SELECT dad.device.id AS deviceId, req.status AS status, req.createdDate AS createdDate "
        + "FROM DeviceApprovalDetail dad "
        + "JOIN dad.request req "
        + "WHERE dad.action = :action")
    List<DisposalStatusProjection> findDisposalStatusRows(@Param("action") DeviceApprovalAction action);

        @Query(value = """
                SELECT dad.device_id,
                             dad.action,
                             req.status,
                             req.id AS request_id,
                             req.due_date,
                             req.created_date,
                             req.requester_name,
                             dad.requested_real_user
                    FROM device_approval_details dad
                    JOIN approval_details ad ON dad.request_id = ad.request_id
                    JOIN approval_requests req ON req.id = ad.request_id
                 WHERE dad.device_id IN :deviceIds
                     AND req.created_date = (
                                SELECT MAX(req2.created_date)
                                    FROM device_approval_details dad2
                                    JOIN approval_details ad2 ON dad2.request_id = ad2.request_id
                                    JOIN approval_requests req2 ON req2.id = ad2.request_id
                                 WHERE dad2.device_id = dad.device_id
                     )
        """, nativeQuery = true)
        List<Object[]> findLatestApprovalSnapshots(@Param("deviceIds") Collection<String> deviceIds);

    interface DisposalStatusProjection {
        String getDeviceId();

        ApprovalStatus getStatus();

        LocalDateTime getCreatedDate();
    }
}
