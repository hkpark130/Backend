package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.dto.DeviceDisposeRequest;
import kr.co.direa.backoffice.dto.DeviceForceReturnRequest;
import kr.co.direa.backoffice.dto.DeviceRecoveryRequest;
import kr.co.direa.backoffice.dto.MyDeviceUpdateRequest;
import kr.co.direa.backoffice.dto.PageResponse;
import kr.co.direa.backoffice.service.DeviceService;
import kr.co.direa.backoffice.vo.AdminDeviceSearchRequest;
import kr.co.direa.backoffice.vo.DeviceSearchRequest;
import kr.co.direa.backoffice.vo.MyDeviceSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping("/available-devicelist")
    public ResponseEntity<PageResponse<DeviceDto>> getAvailableDevices(DeviceSearchRequest request) {
        return ResponseEntity.ok(deviceService.findAvailableDevices(request));
    }

    @GetMapping("/available-devices/counts")
    public ResponseEntity<Map<String, Long>> getAvailableDeviceCounts() {
        return ResponseEntity.ok(deviceService.findAvailableDeviceCountsByCategory());
    }

    @GetMapping("/my-devices")
    public ResponseEntity<PageResponse<DeviceDto>> getMyDevices(MyDeviceSearchRequest request) {
        return ResponseEntity.ok(deviceService.findDevicesForCurrentUser(request));
    }

    @PatchMapping("/my-devices/{id}")
    public ResponseEntity<DeviceDto> updateMyDevice(@PathVariable String id,
                                                    @RequestBody MyDeviceUpdateRequest request) {
        return ResponseEntity.ok(deviceService.updateMyDeviceDescription(id, request.description()));
    }

    @GetMapping("/admin/devices")
    // TODO 인증 연동 시: 관리자 권한 확인 후 제공하도록 Security 설정 추가 필요
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<PageResponse<DeviceDto>> getAdminDevices(AdminDeviceSearchRequest request) {
        return ResponseEntity.ok(deviceService.findAllDevicesForAdmin(request));
    }

    @GetMapping("/admin/devices/disposed")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<PageResponse<DeviceDto>> getDisposedDevices(AdminDeviceSearchRequest request) {
        return ResponseEntity.ok(deviceService.findDisposedDevicesForAdmin(request));
    }

    @GetMapping("/device/{id}")
    // TODO 인증 연동 시: 장비 상세 조회 역시 로그인 사용자 권한 확인 후 제공하도록 보호 필요
    public ResponseEntity<DeviceDto> getDevice(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }

    @PutMapping("/device/{id}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<DeviceDto> updateDevice(@PathVariable String id, @RequestBody DeviceDto dto) {
        return ResponseEntity.ok(deviceService.updateDevice(id, dto));
    }

    @PostMapping("/admin/devices/{id}/dispose")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<DeviceDto> disposeDevice(@PathVariable String id,
                                                   @RequestBody(required = false) DeviceDisposeRequest request) {
        String reason = request != null ? request.reason() : null;
        String operator = request != null ? request.operatorUsername() : null;
        return ResponseEntity.ok(deviceService.disposeDeviceByAdmin(id, reason, operator));
    }

    @PostMapping("/admin/devices/{id}/recover")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<DeviceDto> recoverDevice(@PathVariable String id,
                                                   @RequestBody(required = false) DeviceRecoveryRequest request) {
        String reason = request != null ? request.reason() : null;
        String operator = request != null ? request.operatorUsername() : null;
        return ResponseEntity.ok(deviceService.recoverDeviceByAdmin(id, reason, operator));
    }

    @PostMapping("/admin/devices/{id}/force-return")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<DeviceDto> forceReturnDevice(@PathVariable String id,
                                                       @RequestBody(required = false) DeviceForceReturnRequest request) {
        String reason = request != null ? request.reason() : null;
        String operator = request != null ? request.operatorUsername() : null;
        return ResponseEntity.ok(deviceService.forceReturnDeviceByAdmin(id, reason, operator));
    }

    // Bulk 등록 API
    @PostMapping("/devices/bulk")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<?> bulkRegisterDevices(@RequestBody List<DeviceDto> deviceDtoList) {
        deviceService.bulkRegisterDevices(deviceDtoList);
        return ResponseEntity.ok("success");
    }

    @GetMapping("/devices/lookup")
    public ResponseEntity<List<DeviceDto>> getDevicesByIds(@RequestParam(name = "ids") List<String> idParams) {
        List<String> ids = idParams.stream()
                .filter(Objects::nonNull)
                .flatMap(raw -> Arrays.stream(raw.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return ResponseEntity.ok(deviceService.findByIds(ids));
    }
}
