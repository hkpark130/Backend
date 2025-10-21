package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Devices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevicesRepository extends JpaRepository<Devices, String> {

    Long countByCategoryIdAndIsUsable(Categories category, boolean isUsable);

    @Query("SELECT d FROM Devices d LEFT JOIN FETCH d.approvalDevices")
    List<Devices> findAllWithApprovals();

    @Query(value = "SELECT d FROM Devices d WHERE d.status <> :status")
    List<Devices> findByStatusNot(@Param("status") String status);

    List<Devices> findByIsUsableTrue();
}
