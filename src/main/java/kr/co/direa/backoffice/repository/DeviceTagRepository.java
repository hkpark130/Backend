package kr.co.direa.backoffice.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kr.co.direa.backoffice.domain.DeviceTag;

public interface DeviceTagRepository extends JpaRepository<DeviceTag, Long> {

    @Query("SELECT dt.device.id, dt.tag.name FROM DeviceTag dt")
    List<Object[]> findAllDeviceTagNames();

    @Query("SELECT dt.device.id, dt.tag.name FROM DeviceTag dt WHERE dt.device.id IN :deviceIds")
    List<Object[]> findTagNamesByDeviceIds(@Param("deviceIds") Collection<String> deviceIds);
}
