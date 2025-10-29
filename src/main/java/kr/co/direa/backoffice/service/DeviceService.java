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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DevicesRepository devicesRepository;
    private final ApprovalDevicesRepository approvalDevicesRepository;
    private final CommonLookupService commonLookupService;

    private static final Pattern OPERATOR_SUFFIX_PATTERN = Pattern.compile("\\(처리자: (?<username>.+?)\\)$");

    // Bulk 등록 메서드
    @Transactional
    public void bulkRegisterDevices(List<DeviceDto> deviceDtoList) {
        for (DeviceDto dto : deviceDtoList) {
            Devices device = new Devices();
            device.setId(dto.getId());
            // categoryName -> categoryId
        commonLookupService.findCategoryByName(dto.getCategoryName())
                    .ifPresent(device::setCategoryId);
            // manageDepName -> manageDep
        commonLookupService.findDepartmentByName(dto.getManageDepName())
                    .ifPresent(device::setManageDep);
            // projectName -> projectId
        commonLookupService.findProjectByName(dto.getProjectName())
                    .ifPresent(device::setProjectId);
            device.setStatus(dto.getStatus());
            device.setPurpose(dto.getPurpose());
            device.setSpec(dto.getSpec());
            device.setPrice(dto.getPrice());
            device.setModel(dto.getModel());
            device.setCompany(dto.getCompany());
            device.setSn(dto.getSn());
            device.setIsUsable(Boolean.valueOf(String.valueOf(dto.getIsUsable())));
            device.setPurchaseDate(dto.getPurchaseDate());
            device.setDescription(dto.getDescription());
            device.setAdminDescription(dto.getAdminDescription());
        // username -> userUuid (Keycloak)
            commonLookupService.resolveKeycloakUserIdByUsername(dto.getUsername())
                    .ifPresent(uuidStr -> {
                        try {
                            device.setUserUuid(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {}
                    });
        // (선택) 기존 Users 연관 유지가 필요하면 아래 주석 해제
        // commonLookupService.findUserByUsername(dto.getUsername())
        //         .ifPresent(device::setUserId);
            devicesRepository.save(device);
        }
    }
    public List<DeviceDto> findAvailableDevices() {
        List<Devices> devices = devicesRepository.findAllWithApprovals();
        return devices.stream()
                .filter(this::isDeviceAvailable)
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
    }

    public List<DeviceDto> findAllDevicesForAdmin() {
        List<Devices> devices = devicesRepository.findAllWithApprovalsAndStatusNot(Constants.DISPOSE_TYPE);
        return devices.stream()
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
    }

    public List<DeviceDto> findDisposedDevicesForAdmin() {
        List<Devices> devices = devicesRepository.findAllWithApprovalsAndStatus(Constants.DISPOSE_TYPE);
        return devices.stream()
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
    }

    public DeviceDto findById(String id) {
        Devices device = devicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        return new DeviceDto(device, buildHistory(id));
    }

    @Transactional
    public DeviceDto disposeDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        String operatorUsername = resolveOperatorUsername(operatorOverride);
        validateOperatorUser(operatorUsername);

        Optional<ApprovalDevices> latestApproval = device.getApprovalDevices().stream()
                .max(Comparator.comparing(ApprovalDevices::getCreatedDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

        latestApproval.ifPresent(approval -> {
            if (Constants.APPROVAL_WAITING.equals(approval.getApprovalInfo())) {
                if (Constants.DISPOSE_TYPE.equals(approval.getType())) {
                    approval.setApprovalInfo(Constants.APPROVAL_COMPLETED);
                } else {
                    approval.setApprovalInfo(Constants.APPROVAL_REJECT);
                }
                approvalDevicesRepository.save(approval);
            }
        });

        device.setStatus(Constants.DISPOSE_TYPE);
        device.setIsUsable(Boolean.FALSE);
        device.setUserId(null);
        device.setRealUser(null);
        device.setUserUuid(null);
        devicesRepository.save(device);

        ApprovalDevices disposalRecord = new ApprovalDevices();
        disposalRecord.setDeviceId(device);
        // Keycloak 사용자 기준으로 처리하며, 로컬 Users 연관은 사용하지 않음
        disposalRecord.setType(Constants.DISPOSE_TYPE);
        disposalRecord.setApprovalInfo(Constants.APPROVAL_COMPLETED);
        disposalRecord.setProjectId(device.getProjectId());
        String trimmedReason = Optional.ofNullable(reason).map(String::trim).orElse("");
        String baseReason = trimmedReason.isEmpty() ? "관리자 즉시 폐기 처리" : trimmedReason;
        disposalRecord.setReason(buildOperatorTaggedReason(baseReason, operatorUsername));
        ApprovalDevices savedRecord = approvalDevicesRepository.save(disposalRecord);
        device.getApprovalDevices().add(savedRecord);

        return new DeviceDto(device, buildHistory(deviceId));
    }

    @Transactional
    public DeviceDto recoverDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!Constants.DISPOSE_TYPE.equals(device.getStatus())) {
            throw new IllegalStateException("Disposed device만 복구할 수 있습니다.");
        }

        String operatorUsername = resolveOperatorUsername(operatorOverride);
        validateOperatorUser(operatorUsername);

        device.setStatus(Constants.NORMAL_TYPE);
        device.setIsUsable(Boolean.TRUE);
        devicesRepository.save(device);

        ApprovalDevices recoveryRecord = new ApprovalDevices();
        recoveryRecord.setDeviceId(device);
        recoveryRecord.setType(Constants.RECOVER_TYPE);
        recoveryRecord.setApprovalInfo(Constants.APPROVAL_COMPLETED);
        recoveryRecord.setProjectId(device.getProjectId());
        String trimmedReason = Optional.ofNullable(reason).map(String::trim).orElse("");
        String baseReason = trimmedReason.isEmpty() ? "관리자 복구 처리" : trimmedReason;
        recoveryRecord.setReason(buildOperatorTaggedReason(baseReason, operatorUsername));

        ApprovalDevices savedRecord = approvalDevicesRepository.save(recoveryRecord);
        device.getApprovalDevices().add(savedRecord);

        return new DeviceDto(device, buildHistory(deviceId));
    }

    @Transactional
    public DeviceDto updateDevice(String id, DeviceDto dto) {
        Devices device = devicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));

        var categoryOpt = commonLookupService.findCategoryByName(dto.getCategoryName());
        var projectOpt = commonLookupService.findProjectByName(dto.getProjectName());
        var deptOpt = commonLookupService.findDepartmentByName(dto.getManageDepName());

        long price = dto.getPrice() == null ? 0L : dto.getPrice();
        device.update(
                categoryOpt.orElse(null),
                projectOpt.orElse(null),
                deptOpt.orElse(null),
                price,
                dto.getStatus(),
                dto.getPurpose(),
                dto.getDescription(),
                dto.getAdminDescription(),
                dto.getModel(),
                dto.getCompany(),
                dto.getSn(),
                dto.getSpec(),
                dto.getPurchaseDate()
        );

        // 사용자 정보 및 실사용자 반영
        device.setRealUser(dto.getRealUser());
        commonLookupService.findUserByUsername(dto.getUsername()).ifPresent(device::setUserId);
        commonLookupService.resolveKeycloakUserIdByUsername(dto.getUsername()).ifPresent(uuidStr -> {
            try { device.setUserUuid(java.util.UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignore) {}
        });

        devicesRepository.save(device);
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
            Optional<String> dbUsername = Optional.ofNullable(history.getUserId()).map(Users::getUsername);
            Optional<String> operatorFromReason = extractOperatorFromReason(history.getReason());
            map.put("username", dbUsername.or(() -> operatorFromReason).orElse("알 수 없음"));
            operatorFromReason.ifPresent(value -> map.put("operatorUsername", value));
            map.put("reason", history.getReason());
        map.put("type", history.getType());
        map.put("projectName", Optional.ofNullable(history.getProjectId())
            .map(project -> project.getName())
            .orElse(null));
            map.put("modifiedDate", history.getModifiedDate());
            historyList.add(map);
        }
        return historyList;
    }

    private String buildOperatorTaggedReason(String baseReason, String operatorUsername) {
        if (operatorUsername == null || operatorUsername.isBlank()) {
            return baseReason;
        }
        String suffix = " (처리자: " + operatorUsername + ")";
        if (baseReason.endsWith(suffix)) {
            return baseReason;
        }
        return baseReason + suffix;
    }

    private String resolveOperatorUsername(String operatorOverride) {
        return commonLookupService.currentUsernameFromJwt()
                .or(() -> Optional.ofNullable(operatorOverride).filter(s -> !s.isBlank()))
                .orElseThrow(() -> new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다."));
    }

    private void validateOperatorUser(String operatorUsername) {
        boolean operatorIsAdmin = commonLookupService.isAdminUser(operatorUsername);
        if (!operatorIsAdmin) {
            commonLookupService.resolveKeycloakUserIdByUsername(operatorUsername)
                    .orElseThrow(() -> new IllegalArgumentException("Keycloak user not found: " + operatorUsername));
        }
    }

    private Optional<String> extractOperatorFromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = OPERATOR_SUFFIX_PATTERN.matcher(reason.trim());
        if (matcher.find()) {
            String username = matcher.group("username");
            if (username != null && !username.isBlank()) {
                return Optional.of(username.trim());
            }
        }
        return Optional.empty();
    }
}
