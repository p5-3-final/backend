package com.vlm.media;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Set;

@AnalyzeClasses(packages = "com.vlm.media", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "com.vlm.media";
  // 기능이 아닌 최상위 패키지. shared는 기능들이 함께 쓰는 Video 집합, config는 앱 전역 설정이다.
  private static final Set<String> NOT_FEATURES = Set.of("shared", "config");

  // AGENTS.md "패키지 구조": 기능·shared 안의 의존 방향은 api → application → domain ← infra.
  // infra의 메시지 소비자·스케줄러는 인바운드 어댑터라 application을 부른다.
  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("api")
          .definedBy(BASE + ".*.api..")
          .layer("application")
          .definedBy(BASE + ".*.application..")
          .layer("domain")
          .definedBy(BASE + ".*.domain..")
          .layer("infra")
          .definedBy(BASE + ".*.infra..")
          .whereLayer("api")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("application")
          .mayOnlyBeAccessedByLayers("api", "infra")
          .whereLayer("infra")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("domain")
          .mayOnlyBeAccessedByLayers("api", "application", "infra");

  // 최상위는 기능, shared, config와 애플리케이션 클래스만 둔다.
  @ArchTest
  static final ArchRule topLevel =
      classes()
          .should()
          .resideInAnyPackage(
              BASE,
              BASE + ".*.api..",
              BASE + ".*.application..",
              BASE + ".*.domain..",
              BASE + ".*.infra..",
              BASE + ".config..");

  // 다른 기능은 그 기능의 application으로만 부른다. shared는 기능을 모른다.
  @ArchTest
  static final ArchRule featureBoundaries =
      classes().should(onlyCallOtherFeaturesThroughApplication());

  @ArchTest
  static final ArchRule noFeatureCycles =
      slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule noOtherServices =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.vlm.gateway..", "com.vlm.auth..", "com.vlm.pipeline..")
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

  private static ArchCondition<JavaClass> onlyCallOtherFeaturesThroughApplication() {
    return new ArchCondition<>("only call other features through their application package") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        String from = feature(origin.getPackageName());
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
          String target = dependency.getTargetClass().getPackageName();
          String to = feature(target);
          if (to == null || to.equals(from) || "config".equals(from)) {
            continue;
          }
          boolean allowed =
              "shared".equals(to)
                  ? !"shared".equals(from)
                  : !NOT_FEATURES.contains(to)
                      && target.startsWith(BASE + "." + to + ".application");
          if (!allowed) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  /** {@code com.vlm.media.<기능>...}의 기능 이름. 서비스 밖이나 최상위면 null이다. */
  private static String feature(String packageName) {
    if (!packageName.startsWith(BASE + ".")) {
      return null;
    }
    String rest = packageName.substring(BASE.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }
}
