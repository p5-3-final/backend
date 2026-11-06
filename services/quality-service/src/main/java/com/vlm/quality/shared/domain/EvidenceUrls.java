package com.vlm.quality.shared.domain;

import java.net.URI;
import java.util.List;

/** 근거 프레임(블러 사본) 경로를 화면이 바로 읽을 수 있는 서명 주소로 바꾼다(SPEC-035 R-4). */
public interface EvidenceUrls {

  /** 경로와 같은 순서의 GET 서명 주소. 주소는 로그·저장소에 남기지 않는다. */
  List<URI> sign(List<String> evidenceFrameKeys);
}
