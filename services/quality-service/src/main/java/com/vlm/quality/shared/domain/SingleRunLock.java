package com.vlm.quality.shared.domain;

/** 여러 Pod 중 하나만 주기 작업을 돌리게 한다(R-10, R-23, R-25). */
public interface SingleRunLock {

  /**
   * 잠금을 잡으면 한 트랜잭션 안에서 작업을 돌린다. 다른 Pod가 잡고 있으면 아무것도 하지 않는다.
   *
   * @param key 작업마다 고정한 잠금 키
   * @return 작업을 돌렸으면 true
   */
  boolean runExclusively(long key, Runnable work);
}
