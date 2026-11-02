package com.vlm.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

@AnalyzeClasses(packages = "com.vlm.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // 게이트웨이는 도메인 레이어가 없어 레이어 규칙 대신 패키지 간 순환만 막는다.
  @ArchTest
  static final ArchRule noCycles =
      slices().matching("com.vlm.gateway.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

  @ArchTest
  static final ArchRule noOtherServices =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.vlm.auth..", "com.vlm.media..", "com.vlm.pipeline..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noFieldInjection =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true);

  @ArchTest
  static final ArchRule noStandardStreams =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule noJavaUtilLogging =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
