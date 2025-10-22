package kr.co.direa.backoffice.dto.ldap;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LdapUserCreateRequest {

    @NotBlank(message = "cn은 필수입니다.")
    private String cn;

    @NotBlank(message = "uid는 필수입니다.")
    private String uid;

    @NotNull(message = "uidNumber는 필수입니다.")
    @Min(value = 1, message = "uidNumber는 1 이상의 값이어야 합니다.")
    private Integer uidNumber;

    @NotBlank(message = "ou는 필수입니다.")
    private String ou;

    @NotBlank(message = "mail은 필수입니다.")
    private String mail;

    @Size(max = 100)
    private String userPassword;

    @NotNull(message = "gidNumber는 필수입니다.")
    private Integer gidNumber;

    @Size(max = 50)
    private String status;

    @Size(max = 150)
    private String description;

    @Size(max = 150)
    private String sn;

    @NotBlank(message = "homeDirectory는 필수입니다.")
    private String homeDirectory;
}
