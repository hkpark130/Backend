package kr.co.direa.backoffice.repository;

import java.util.List;
import java.util.Optional;
import kr.co.direa.backoffice.domain.ldap.LdapUser;
import org.springframework.data.ldap.repository.LdapRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LdapUserRepository extends LdapRepository<LdapUser> {

    Optional<LdapUser> findByCn(String cn);

    Optional<LdapUser> findByUidNumber(Integer uidNumber);

    @Override
    List<LdapUser> findAll();
}
