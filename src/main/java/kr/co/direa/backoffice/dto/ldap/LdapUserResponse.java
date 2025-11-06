package kr.co.direa.backoffice.dto.ldap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LdapUserResponse {

    private String cn;
    private String uid;
    private Integer uidNumber;
    private String mail;
    private String ou;
    private String description;
    private Integer gidNumber;
    private String homeDirectory;
    private String status;
}
