package kr.co.direa.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenStackInstanceDto {
    private String id;
    private String name;
    private String status;
    private String flavor;
    private String image;
    private String host;
    private String privateIp;
    private String floatingIp;
    private String internalInstanceName;
    private String keyName;
    private List<SecurityGroupRuleDto> securityGroups;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityGroupRuleDto {
        private String key;
        private String groupName;
        private String direction;
        private String protocol;
        private String portRange;
        private String remote;
        private String ethertype;
    }
}
