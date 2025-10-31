package kr.co.direa.backoffice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.co.direa.backoffice.domain.DeviceApprovalDetail;

public interface DeviceApprovalDetailRepository extends JpaRepository<DeviceApprovalDetail, Long> {

    @Query("SELECT dad FROM DeviceApprovalDetail dad " +
        "JOIN FETCH dad.request req " +
            "LEFT JOIN FETCH dad.device dev " +
            "LEFT JOIN FETCH dad.requestedProject " +
            "LEFT JOIN FETCH dad.requestedDepartment " +
            "WHERE dev.id = :deviceId " +
            "ORDER BY req.createdDate DESC")
    List<DeviceApprovalDetail> findHistoryByDevice(@Param("deviceId") String deviceId);
}
