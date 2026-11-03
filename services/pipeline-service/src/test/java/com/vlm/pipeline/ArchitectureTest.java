package com.vlm.pipeline;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

@AnalyzeClasses(packages = "com.vlm.pipeline", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // AGENTS.md: 의존 방향은 api → application → domain ← infra
  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("api")
          .definedBy("com.vlm.pipeline.api..")
          .layer("application")
          .definedBy("com.vlm.pipeline.application..")
          .layer("domain")
          .definedBy("com.vlm.pipeline.domain..")
          .layer("infra")
          .definedBy("com.vlm.pipeline.infra..")
          .whereLayer("api")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("application")
          .mayOnlyBeAccessedByLayers("api")
          .whereLayer("infra")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("domain")
          .mayOnlyBeAccessedByLayers("api", "application", "infra");

  @ArchTest
  static final ArchRule noOtherServices =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.vlm.gateway..",
              "com.vlm.media..",
              "com.vlm.auth..",
              "com.vlm.analysis..",
              "com.vlm.search..",
              "com.vlm.notification..")
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

  @ArchTest
  static final ArchRule noLegacyDateTime =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Date")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Calendar");
}
