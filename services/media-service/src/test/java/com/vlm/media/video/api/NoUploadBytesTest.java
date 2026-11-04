package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@IntegrationTest
class NoUploadBytesTest {

  private static final List<Class<?>> BYTE_TYPES =
      List.of(MultipartFile.class, byte[].class, InputStream.class, Resource.class);

  @Autowired MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  @Test
  @DisplayName("[SPEC-003 NFR-1] 영상 파일을 multipart/form-data로 보내면 받지 않는다")
  void rejectsMultipartUpload() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[] {1, 2, 3});

    mvc.perform(asUser(multipart("/api/v1/media/videos").file(file), UUID.randomUUID()))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("[SPEC-003 NFR-1] 어떤 엔드포인트도 파일 바이트를 인자로 받지 않는다")
  void noHandlerTakesBytes() {
    List<String> handlersTakingBytes =
        handlerMapping.getHandlerMethods().values().stream()
            .filter(handler -> handler.getBeanType().getPackageName().startsWith("com.vlm.media"))
            .filter(
                handler ->
                    Arrays.stream(handler.getMethodParameters())
                        .anyMatch(
                            p ->
                                BYTE_TYPES.stream()
                                    .anyMatch(t -> t.isAssignableFrom(p.getParameterType()))))
            .map(handler -> handler.getShortLogMessage())
            .toList();

    assertThat(handlersTakingBytes).isEmpty();
  }
}
