package com.vlm.quality.dataset.domain;

/**
 * 가져오기 오류 목록(계약 {@code DatasetImport.errors[].code})의 값. 영상별 오류는 HTTP 응답이 아니라 가져오기 상태에 남는다(R-7).
 */
public final class ImportErrorCodes {

  public static final String MANIFEST_NOT_FOUND = "QUALITY_IMPORT_MANIFEST_NOT_FOUND";
  public static final String MANIFEST_INVALID = "QUALITY_IMPORT_MANIFEST_INVALID";
  public static final String TAXONOMY_UNSUPPORTED = "QUALITY_IMPORT_TAXONOMY_UNSUPPORTED";
  public static final String VIDEO_NOT_FOUND = "QUALITY_IMPORT_VIDEO_NOT_FOUND";
  public static final String VIDEO_REJECTED = "QUALITY_IMPORT_VIDEO_REJECTED";
  public static final String MEDIA_UNAVAILABLE = "QUALITY_IMPORT_MEDIA_UNAVAILABLE";
  public static final String NO_VIDEO_REGISTERED = "QUALITY_IMPORT_NO_VIDEO_REGISTERED";
  public static final String GOLDEN_ORIGIN_MISMATCH = "QUALITY_GOLDEN_ORIGIN_MISMATCH";

  private ImportErrorCodes() {}
}
