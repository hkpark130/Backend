package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.ApprovalDevices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApprovalDevicesRepository extends JpaRepository<ApprovalDevices, Long> {

    @Query("SELECT ad FROM ApprovalDevices ad JOIN FETCH ad.deviceId d WHERE d.id = :deviceId ORDER BY ad.createdDate DESC")
    List<ApprovalDevices> findHistoryByDeviceId(@Param("deviceId") String deviceId);

    @Query("SELECT ad FROM ApprovalDevices ad JOIN FETCH ad.userId u WHERE u.username = :username ORDER BY ad.createdDate DESC")
    List<ApprovalDevices> findAllByUsername(@Param("username") String username);
}
