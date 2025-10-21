package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/device/{id}")
    // TODO 인증 연동 시: 장비 상세 조회 역시 로그인 사용자 권한 확인 후 제공하도록 보호 필요
    public ResponseEntity<DeviceDto> getDevice(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.findById(id));
    }
}
