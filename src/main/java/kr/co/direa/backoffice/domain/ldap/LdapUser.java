package kr.co.direa.backoffice.domain.ldap;

import javax.naming.Name;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;

@Getter
@Setter
@NoArgsConstructor
@Entry(objectClasses = {"inetOrgPerson", "posixAccount", "top"}, base = "cn=Users")
public class LdapUser {

    @Id
    private Name dn;

    @Attribute(name = "cn")
    private String cn;

    @Attribute(name = "sn")
    private String sn;

    @Attribute(name = "entryUUID")
    private String entryUUID;

    @Attribute(name = "gidNumber")
    private Integer gidNumber;

    @Attribute(name = "uidNumber")
    private Integer uidNumber;

    @Attribute(name = "uid")
    private String uid;

    @Attribute(name = "mail")
    private String mail;

    @Attribute(name = "userPassword")
    private String userPassword;

    @Attribute(name = "homeDirectory")
    private String homeDirectory;

    @Attribute(name = "ou")
    private String ou;

    @Attribute(name = "description")
    private String description;

    @Attribute(name = "employeeType")
    private String employeeType;

    @Attribute(name = "loginShell")
    private String loginShell;

    @Builder
    public LdapUser(Name dn,
                    String cn,
                    String sn,
                    Integer gidNumber,
                    Integer uidNumber,
                    String uid,
                    String mail,
                    String userPassword,
                    String homeDirectory,
                    String ou,
                    String description,
                    String employeeType,
                    String loginShell) {
        this.dn = dn;
        this.cn = cn;
        this.sn = sn;
        this.gidNumber = gidNumber;
        this.uidNumber = uidNumber;
        this.uid = uid;
        this.mail = mail;
        this.userPassword = userPassword;
        this.homeDirectory = homeDirectory;
        this.ou = ou;
        this.description = description;
        this.employeeType = employeeType;
        this.loginShell = loginShell;
    }
}
