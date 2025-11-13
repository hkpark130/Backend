package kr.co.direa.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Request payload carrying device application data from the front-end.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceApplicationRequestDto {
    private Long approvalId;
    private String userName;
    private String realUser;
    private String realUserMode;
    private String reason;
    private String deviceId;
    private List<String> deviceIds = new ArrayList<>();
    private String deviceStatus;
    private String devicePurpose;
    private String description;
    private String categoryName;
    private String projectName;
    private String projectCode;
    private String departmentName;
    private String img;
    private String type;
    private String status;
    private Boolean isUsable;
    private List<DeviceSelection> devices = new ArrayList<>();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deadline;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime usageStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime usageEndDate;
    private List<String> tag = new ArrayList<>();
    private List<String> approvers = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceSelection {
        private String deviceId;
        private String departmentName;
        private String projectName;
        private String projectCode;
        private String realUser;
        private String realUserMode;
        private String status;
        private List<String> tags = new ArrayList<>();
    }
}
