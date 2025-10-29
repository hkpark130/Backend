package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.dto.DeviceDisposeRequest;
import kr.co.direa.backoffice.dto.DeviceRecoveryRequest;
import kr.co.direa.backoffice.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping("/available-devicelist")
    // TODO 인증 연동 시: 로그인한 사용자만 조회할 수 있도록 Security 설정 추가 필요
    public ResponseEntity<List<DeviceDto>> getAvailableDevices() {
        return ResponseEntity.ok(deviceService.findAvailableDevices());
    }

    @GetMapping("/admin/devices")
    // TODO 인증 연동 시: 관리자 권한 확인 후 제공하도록 Security 설정 추가 필요
    public ResponseEntity<List<DeviceDto>> getAdminDevices() {
        return ResponseEntity.ok(deviceService.findAllDevicesForAdmin());
    }

    @GetMapping("/admin/devices/disposed")
    public ResponseEntity<List<DeviceDto>> getDisposedDevices() {
        return ResponseEntity.ok(deviceService.findDisposedDevicesForAdmin());
    }

    @GetMapping("/device/{id}")
    // TODO 인증 연동 시: 장비 상세 조회 역시 로그인 사용자 권한 확인 후 제공하도록 보호 필요
    public ResponseEntity<DeviceDto> getDevice(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }

    @PutMapping("/device/{id}")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable String id, @RequestBody DeviceDto dto) {
        return ResponseEntity.ok(deviceService.updateDevice(id, dto));
    }

    @PostMapping("/admin/devices/{id}/dispose")
    public ResponseEntity<DeviceDto> disposeDevice(@PathVariable String id,
                                                   @RequestBody(required = false) DeviceDisposeRequest request) {
        String reason = request != null ? request.reason() : null;
        String operator = request != null ? request.operatorUsername() : null;
        return ResponseEntity.ok(deviceService.disposeDeviceByAdmin(id, reason, operator));
    }

    @PostMapping("/admin/devices/{id}/recover")
    public ResponseEntity<DeviceDto> recoverDevice(@PathVariable String id,
                                                   @RequestBody(required = false) DeviceRecoveryRequest request) {
        String reason = request != null ? request.reason() : null;
        String operator = request != null ? request.operatorUsername() : null;
        return ResponseEntity.ok(deviceService.recoverDeviceByAdmin(id, reason, operator));
    }

    // Bulk 등록 API
    @PostMapping("/devices/bulk")
    public ResponseEntity<?> bulkRegisterDevices(@RequestBody List<DeviceDto> deviceDtoList) {
        deviceService.bulkRegisterDevices(deviceDtoList);
        return ResponseEntity.ok("success");
    }
}
