package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.dto.ApprovalCommentDto;
import kr.co.direa.backoffice.dto.ApprovalDeviceDto;
import kr.co.direa.backoffice.dto.ApproverCandidateDto;
import kr.co.direa.backoffice.dto.DeviceApplicationRequestDto;
import kr.co.direa.backoffice.dto.PageResponse;
import kr.co.direa.backoffice.service.ApprovalCommentService;
import kr.co.direa.backoffice.service.ApprovalDeviceService;
import kr.co.direa.backoffice.service.ApproverDirectoryService;
import kr.co.direa.backoffice.vo.ApprovalActionRequest;
import kr.co.direa.backoffice.vo.ApprovalCommentRequest;
import kr.co.direa.backoffice.vo.ApprovalCommentUpdateRequest;
import kr.co.direa.backoffice.vo.ApproverUpdateRequest;
import kr.co.direa.backoffice.vo.ApprovalSearchRequest;
import kr.co.direa.backoffice.vo.ApprovalUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApprovalController {
    private final ApprovalDeviceService approvalDeviceService;
    private final ApprovalCommentService approvalCommentService;
    private final ApproverDirectoryService approverDirectoryService;

    @PostMapping("/device-application")
    // TODO 인증 연동 시: 신청자는 로그인된 사용자만 가능하도록 권한 체크 추가 예정
    public ResponseEntity<String> submitDeviceApplication(@RequestBody DeviceApplicationRequestDto request) {
        ApprovalDeviceDto dto = approvalDeviceService.submitApplication(request);
        return ResponseEntity.ok(dto.getApprovalInfo() != null ? dto.getApprovalInfo() : Constants.SUCCESS);
    }

    @GetMapping("/approvals/default-approvers")
    public ResponseEntity<List<ApproverCandidateDto>> getDefaultApprovers() {
        return ResponseEntity.ok(approverDirectoryService.getDefaultApprovers());
    }

    @GetMapping("/approvals/pending")
    // TODO 인증 연동 시: 관리자/승인자 역할만 목록 조회 허용하도록 Security 규칙 구성 필요
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<PageResponse<ApprovalDeviceDto>> getPendingApprovals(ApprovalSearchRequest searchRequest) {
        return ResponseEntity.ok(approvalDeviceService.findPendingApprovals(searchRequest));
    }

    @GetMapping("/my-approval-list/{username}")
    public ResponseEntity<List<ApprovalDeviceDto>> getMyApprovals(@PathVariable String username) {
        return ResponseEntity.ok(approvalDeviceService.findApprovalsForUser(username));
    }

    @GetMapping("/approvals/{approvalId}")
    // TODO 인증 연동 시: 승인자 또는 신청자 본인만 상세 조회 가능하도록 제한 예정
    public ResponseEntity<ApprovalDeviceDto> getApproval(@PathVariable Long approvalId) {
        return ResponseEntity.ok(approvalDeviceService.findDetail(approvalId));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    // TODO 인증 연동 시: 현재 단계 승인자만 승인 API 호출 가능하도록 권한 필수
    public ResponseEntity<ApprovalDeviceDto> approve(@PathVariable Long approvalId,
                                                     @RequestBody ApprovalActionRequest request) {
        return ResponseEntity.ok(approvalDeviceService.approve(
                approvalId,
                request.getApproverUsername(),
                request.getComment()
        ));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    // TODO 인증 연동 시: 승인자 본인만 반려 처리 가능하도록 권한 확인 필요
    public ResponseEntity<ApprovalDeviceDto> reject(@PathVariable Long approvalId,
                                                    @RequestBody ApprovalActionRequest request) {
        return ResponseEntity.ok(approvalDeviceService.reject(
                approvalId,
                request.getApproverUsername(),
                request.getComment()
        ));
    }

    @PutMapping("/approvals/{approvalId}")
    public ResponseEntity<ApprovalDeviceDto> updateApproval(@PathVariable Long approvalId,
                                                            @RequestBody ApprovalUpdateRequest request) {
        return ResponseEntity.ok(approvalDeviceService.updateApplication(approvalId, request));
    }

    @PutMapping("/approvals/{approvalId}/approvers")
    // TODO 인증 연동 시: 관리자 권한에서만 결재선 변경 허용하도록 보호 필요
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<ApprovalDeviceDto> updateApprovers(@PathVariable Long approvalId,
                                                             @RequestBody ApproverUpdateRequest request) {
        return ResponseEntity.ok(approvalDeviceService.updateApprovers(approvalId, request.getApproverUsernames()));
    }

    @GetMapping("/approvals/{approvalId}/comments")
    // TODO 인증 연동 시: 승인자/신청자만 댓글 열람 가능하도록 제한 예정
    public ResponseEntity<List<ApprovalCommentDto>> getComments(@PathVariable Long approvalId) {
        return ResponseEntity.ok(approvalCommentService.findByApproval(approvalId));
    }

    @PostMapping("/approvals/{approvalId}/comments")
    // TODO 인증 연동 시: 로그인 사용자만 댓글 작성 가능하도록 권한 체크 필요
    public ResponseEntity<ApprovalCommentDto> addComment(@PathVariable Long approvalId,
                                                         @RequestBody ApprovalCommentRequest request) {
        return ResponseEntity.ok(approvalCommentService.addComment(approvalId, request.getUsername(), request.getContent()));
    }

    @PutMapping("/approvals/{approvalId}/comments/{commentId}")
    // TODO 인증 연동 시: 댓글 작성자만 수정 가능하도록 권한 체크 필요
    public ResponseEntity<ApprovalCommentDto> updateComment(@PathVariable Long approvalId,
                                                            @PathVariable Long commentId,
                                                            @RequestBody ApprovalCommentUpdateRequest request) {
        return ResponseEntity.ok(approvalCommentService.updateComment(
                approvalId,
                commentId,
                request.getUsername(),
                request.getContent()
        ));
    }

    @DeleteMapping("/approvals/{approvalId}/comments/{commentId}")
    // TODO 인증 연동 시: 댓글 작성자만 삭제 가능하도록 권한 체크 필요
    public ResponseEntity<Void> deleteComment(@PathVariable Long approvalId,
                                              @PathVariable Long commentId,
                                              @RequestParam String username) {
        approvalCommentService.deleteComment(approvalId, commentId, username);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/approvals/{approvalId}/reason")
    public ResponseEntity<ApprovalDeviceDto> updateReason(@PathVariable Long approvalId,
                                                          @RequestBody ApprovalUpdateRequest request) {
    return ResponseEntity.ok(approvalDeviceService.updateApplication(approvalId, request));
    }

    @DeleteMapping("/approval-device-cancel/{approvalId}")
    public ResponseEntity<String> cancelApproval(@PathVariable Long approvalId,
                                                 @RequestParam(required = false) String username) {
        approvalDeviceService.cancelApproval(approvalId, username);
        return ResponseEntity.ok(Constants.SUCCESS);
    }
}
