package com.example.agent.orchestration.multiagent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * MultiAgent 包依赖守护测试。
 * <p>用途：防止 dag 子域分层回退，保证依赖方向持续收敛。</p>
 */
@AnalyzeClasses(packages = "com.example.agent.orchestration.multiagent",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class MultiAgentPackageArchitectureTest {

    /**
     * 领域层禁止依赖基础设施实现层。
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnInfrastructure =
            noClasses()
                    .that()
                    .resideInAPackage("..dag.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..dag.infrastructure..");

    /**
     * 应用层禁止依赖 HTTP 控制器。
     */
    @ArchTest
    static final ArchRule applicationShouldNotDependOnHttpController =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..api.http.controller..");

    /**
     * 基础设施层禁止依赖 MultiAgent 用例层。
     */
    @ArchTest
    static final ArchRule infrastructureShouldNotDependOnUseCase =
            noClasses()
                    .that()
                    .resideInAPackage("..dag.infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..multiagent.usecase..");
}

