package com.trading.repository;

import com.trading.model.DhanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DhanAccountRepository extends JpaRepository<DhanAccount, Long> {
    Optional<DhanAccount> findByClientId(String clientId);
    Optional<DhanAccount> findByIsActiveTrue();
}