package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalDevices;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.repository.ApprovalDevicesRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DevicesRepository devicesRepository;
    private final ApprovalDevicesRepository approvalDevicesRepository;
    public List<DeviceDto> findAvailableDevices() {
        List<Devices> devices = devicesRepository.findAllWithApprovals();
        return devices.stream()
                .filter(this::isDeviceAvailable)
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
    }

    public DeviceDto findById(String id) {
        Devices device = devicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        return new DeviceDto(device, buildHistory(id));
    }

    private boolean isDeviceAvailable(Devices device) {
        Optional<ApprovalDevices> latestApproval = device.getApprovalDevices().stream()
                .max(Comparator.comparing(ApprovalDevices::getCreatedDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        if (latestApproval.isEmpty()) {
            return Boolean.TRUE.equals(device.getIsUsable());
        }

        ApprovalDevices approval = latestApproval.get();
        boolean returnWaiting = Constants.APPROVAL_RETURN.equals(approval.getType()) &&
                (Constants.APPROVAL_WAITING.equals(approval.getApprovalInfo()) ||
                        Constants.APPROVAL_COMPLETED.equals(approval.getApprovalInfo()));
        boolean rentalRejected = Constants.APPROVAL_RENTAL.equals(approval.getType()) &&
                Constants.APPROVAL_REJECT.equals(approval.getApprovalInfo());

        return returnWaiting || rentalRejected || Boolean.TRUE.equals(device.getIsUsable());
    }

    private List<Map<String, Object>> buildHistory(String deviceId) {
        List<ApprovalDevices> histories = approvalDevicesRepository.findHistoryByDeviceId(deviceId);
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (ApprovalDevices history : histories) {
            Map<String, Object> map = new HashMap<>();
            map.put("username", Optional.ofNullable(history.getUserId()).map(Users::getUsername).orElse("알 수 없음"));
            map.put("type", history.getType());
            map.put("modifiedDate", history.getModifiedDate());
            historyList.add(map);
        }
        return historyList;
    }
}
