package com.vlm.quality.export.application;

import com.vlm.quality.export.domain.SourceResultRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 삭제 전파가 부르는 결과 파일 기록 삭제. 트랜잭션은 호출하는 쪽이 연다. */
@Service
@RequiredArgsConstructor
public class SourceResultDeletion {

  private final SourceResultRepository results;

  public int deleteSource(UUID sourceId) {
    return results.deleteBySource(sourceId);
  }
}
