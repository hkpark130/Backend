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
        "requestedDepartment",
        "items",
        "items.device"
    })
    @Query("SELECT DISTINCT dad FROM DeviceApprovalDetail dad " +
        "LEFT JOIN dad.items items " +
        "WHERE dad.device.id = :deviceId " +
        "   OR items.device.id = :deviceId " +
        "ORDER BY dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findHistoryByDevice(@Param("deviceId") String deviceId);

    @EntityGraph(attributePaths = {
        "request",
        "items",
        "items.device"
    })
    @Query("SELECT DISTINCT dad FROM DeviceApprovalDetail dad " +
        "LEFT JOIN dad.items items " +
        "WHERE (dad.device.id = :deviceId OR items.device.id = :deviceId) " +
        "AND dad.request.status IN :statuses " +
        "ORDER BY dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findActiveByDeviceIdAndStatuses(
        @Param("deviceId") String deviceId,
        @Param("statuses") List<ApprovalStatus> statuses);

    @EntityGraph(attributePaths = {
        "request",
        "device",
        "requestedProject",
        "requestedDepartment",
        "items",
        "items.device"
    })
    @Query("SELECT DISTINCT dad FROM DeviceApprovalDetail dad " +
        "LEFT JOIN dad.items items " +
        "WHERE dad.device.id IN :deviceIds " +
        "   OR items.device.id IN :deviceIds " +
        "ORDER BY COALESCE(items.device.id, dad.device.id) ASC, dad.request.createdDate DESC")
    List<DeviceApprovalDetail> findHistoryByDeviceIds(@Param("deviceIds") Collection<String> deviceIds);

    @EntityGraph(attributePaths = {
        "request",
        "device",
        "items",
        "items.device"
    })
    List<DeviceApprovalDetail> findByAction(DeviceApprovalAction action);

    @EntityGraph(attributePaths = {
        "request",
        "device",
        "items",
        "items.device"
    })
    @Query("SELECT DISTINCT dad FROM DeviceApprovalDetail dad LEFT JOIN dad.items items " +
        "WHERE (dad.device.id IN :deviceIds OR items.device.id IN :deviceIds) " +
        "AND dad.action = :action")
    List<DeviceApprovalDetail> findByDeviceIdInAndAction(@Param("deviceIds") Collection<String> deviceIds,
                                                         @Param("action") DeviceApprovalAction action);

    @Query("SELECT COALESCE(items.device.id, dad.device.id) AS deviceId, req.status AS status, req.createdDate AS createdDate "
        + "FROM DeviceApprovalDetail dad "
        + "JOIN dad.request req "
        + "LEFT JOIN dad.items items "
        + "WHERE dad.action = :action")
    List<DisposalStatusProjection> findDisposalStatusRows(@Param("action") DeviceApprovalAction action);

       @Query(value = """
             SELECT ranked.device_id,
                   ranked.action,
                   ranked.status,
                   ranked.request_id,
                   ranked.due_date,
                   ranked.created_date,
                   ranked.requester_name,
                   ranked.requested_real_user
               FROM (
                    SELECT source.device_id,
                         source.action,
                         source.status,
                         source.request_id,
                         source.due_date,
                         source.created_date,
                         source.requester_name,
                         source.requested_real_user,
                         ROW_NUMBER() OVER (PARTITION BY source.device_id ORDER BY source.created_date DESC, source.request_id DESC) AS rn
                     FROM (
                     SELECT items.device_id,
                                dad.action,
                                req.status,
                                req.id AS request_id,
                                req.due_date,
                                req.created_date,
                                req.requester_name,
                                items.requested_real_user
                            FROM device_approval_items items
                            JOIN device_approval_details dad ON dad.request_id = items.detail_id
                            JOIN approval_details ad ON dad.request_id = ad.request_id
                            JOIN approval_requests req ON req.id = ad.request_id
                           WHERE items.device_id IN :deviceIds
                          UNION ALL
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
                            AND NOT EXISTS (
                                SELECT 1 FROM device_approval_items items2 WHERE items2.detail_id = dad.request_id
                            )
                         ) source
                   ) ranked
              WHERE ranked.rn = 1
       """, nativeQuery = true)
       List<Object[]> findLatestApprovalSnapshots(@Param("deviceIds") Collection<String> deviceIds);

    interface DisposalStatusProjection {
        String getDeviceId();

        ApprovalStatus getStatus();

        LocalDateTime getCreatedDate();
    }
}
