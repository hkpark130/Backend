package kr.co.direa.backoffice.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import kr.co.direa.backoffice.dto.DeviceApplicationRequestDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalUpdateRequest {
    private String username;
    private String reason;
    private String realUser;
    private String realUserMode;
    private String departmentName;
    private String projectName;
    private String projectCode;
    private List<String> tags;
    private List<DeviceApplicationRequestDto.DeviceSelection> devices = new ArrayList<>();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deadline;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime usageStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime usageEndDate;
}
