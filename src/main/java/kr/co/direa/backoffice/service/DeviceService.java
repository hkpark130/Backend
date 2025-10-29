package kr.co.direa.backoffice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.DeviceApprovalDetailRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DevicesRepository devicesRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DeviceApprovalDetailRepository deviceApprovalDetailRepository;
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

        findLatestDetail(device).ifPresent(detail -> {
            ApprovalRequest request = detail.getRequest();
            if (request == null) {
                return;
            }
            if (detail.getAction() == DeviceApprovalAction.DISPOSAL
                    && request.getStatus() != ApprovalStatus.APPROVED) {
                request.markApproved();
                approvalRequestRepository.save(request);
            } else if (request.getStatus() == ApprovalStatus.PENDING
                    || request.getStatus() == ApprovalStatus.IN_PROGRESS) {
                request.markRejected();
                approvalRequestRepository.save(request);
            }
        });

        device.setStatus(Constants.DISPOSE_TYPE);
        device.setIsUsable(Boolean.FALSE);
        device.setUserId(null);
        device.setRealUser(null);
        device.setUserUuid(null);
        devicesRepository.save(device);

        ApprovalRequest disposalRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.DISPOSAL, device))
                .reason(buildOperatorTaggedReason(
                        Optional.ofNullable(reason).map(String::trim).filter(r -> !r.isEmpty())
                                .orElse("관리자 즉시 폐기 처리"),
                        operatorUsername))
                .build();
        disposalRequest.setSubmittedAt(LocalDateTime.now());
        disposalRequest.setCompletedAt(LocalDateTime.now());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.DISPOSAL);
        detail.setRequestedStatus(Constants.DISPOSE_TYPE);
        detail.setRequestedProject(device.getProjectId());
        detail.setRequestedRealUser(operatorUsername);
        detail.attachTo(disposalRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(disposalRequest);

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

        ApprovalRequest recoveryRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.RECOVERY, device))
                .reason(buildOperatorTaggedReason(
                        Optional.ofNullable(reason).map(String::trim).filter(r -> !r.isEmpty())
                                .orElse("관리자 복구 처리"),
                        operatorUsername))
                .build();
        recoveryRequest.setSubmittedAt(LocalDateTime.now());
        recoveryRequest.setCompletedAt(LocalDateTime.now());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.RECOVERY);
        detail.setRequestedStatus(Constants.NORMAL_TYPE);
        detail.setRequestedProject(device.getProjectId());
        detail.setRequestedRealUser(operatorUsername);
        detail.attachTo(recoveryRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(recoveryRequest);

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
        Optional<DeviceApprovalDetail> latestDetail = findLatestDetail(device);
        if (latestDetail.isEmpty()) {
            return Boolean.TRUE.equals(device.getIsUsable());
        }

        DeviceApprovalDetail detail = latestDetail.get();
        ApprovalRequest request = detail.getRequest();
        DeviceApprovalAction action = detail.getAction();
        ApprovalStatus status = request != null ? request.getStatus() : null;

        boolean returnWaiting = action == DeviceApprovalAction.RETURN &&
                (status == ApprovalStatus.PENDING || status == ApprovalStatus.APPROVED);
        boolean rentalRejected = action == DeviceApprovalAction.RENTAL && status == ApprovalStatus.REJECTED;

        return returnWaiting || rentalRejected || Boolean.TRUE.equals(device.getIsUsable());
    }

    private Optional<DeviceApprovalDetail> findLatestDetail(Devices device) {
        return device.getApprovalDetails().stream()
                .max(Comparator.comparing(
                        detail -> Optional.ofNullable(detail.getRequest())
                                .map(ApprovalRequest::getCreatedDate)
                                .orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private List<Map<String, Object>> buildHistory(String deviceId) {
        List<DeviceApprovalDetail> histories = deviceApprovalDetailRepository.findHistoryByDevice(deviceId);
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (DeviceApprovalDetail detail : histories) {
            Map<String, Object> map = new HashMap<>();
            ApprovalRequest request = detail.getRequest();

            Optional<String> requesterName = Optional.ofNullable(request)
                    .map(ApprovalRequest::getRequesterName)
                    .filter(name -> name != null && !name.isBlank());
            if (requesterName.isEmpty()) {
                requesterName = Optional.ofNullable(request)
                        .map(ApprovalRequest::getRequester)
                        .map(Users::getUsername);
            }

            Optional<String> operatorFromReason = extractOperatorFromReason(
                    Optional.ofNullable(request).map(ApprovalRequest::getReason).orElse(null));

            map.put("username", requesterName.or(() -> operatorFromReason).orElse("알 수 없음"));
            operatorFromReason.ifPresent(value -> map.put("operatorUsername", value));
            map.put("reason", Optional.ofNullable(request).map(ApprovalRequest::getReason).orElse(detail.getMemo()));
            map.put("type", Optional.ofNullable(detail.getAction()).map(DeviceApprovalAction::getDisplayName).orElse(null));
            map.put("projectName", Optional.ofNullable(detail.getRequestedProject())
                    .map(Projects::getName)
                    .orElse(Optional.ofNullable(detail.getDevice())
                            .map(Devices::getProjectId)
                            .map(project -> project != null ? project.getName() : null)
                            .orElse(null)));
            map.put("modifiedDate", Optional.ofNullable(request).map(ApprovalRequest::getModifiedDate).orElse(null));
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

    private String buildSystemTitle(DeviceApprovalAction action, Devices device) {
        String deviceId = device != null ? device.getId() : null;
        if (deviceId == null || deviceId.isBlank()) {
            return "시스템 " + action.getDisplayName();
        }
        return "시스템 " + action.getDisplayName() + " - " + deviceId;
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
