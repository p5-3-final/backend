package com.vlm.quality.shared.infra.persistence;

import com.vlm.quality.shared.domain.SingleRunLock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL 트랜잭션 advisory lock으로 한 Pod만 작업을 돌린다.
 *
 * <p>ShedLock 같은 새 라이브러리 없이 DB 기본 기능만 쓴다(plan "한 Pod만 도는 주기 작업"). 잠금은 트랜잭션이 끝나면 풀려 Pod가 죽어도 남지 않는다.
 */
@Component
@RequiredArgsConstructor
class AdvisorySingleRunLock implements SingleRunLock {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  @Override
  public boolean runExclusively(long key, Runnable work) {
    Boolean ran =
        transactions.execute(
            status -> {
              Boolean locked =
                  jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, key);
              if (!Boolean.TRUE.equals(locked)) {
                return false;
              }
              work.run();
              return true;
            });
    return Boolean.TRUE.equals(ran);
  }
}
