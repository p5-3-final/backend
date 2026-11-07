package com.vlm.quality.export.domain;

import java.nio.file.Path;

/** 내보내기 버킷에 파일을 올린다. */
public interface ExportFiles {

  void upload(String key, Path file);
}
