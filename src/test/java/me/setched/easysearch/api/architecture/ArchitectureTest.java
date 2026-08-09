package me.setched.easysearch.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the project's hexagonal layer boundaries as build-breaking rules, so a framework or
 * infrastructure dependency can't accidentally leak into the domain layer.
 */
@AnalyzeClasses(packages = "me.setched.easysearch.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Verifies that domain is only depended on by application/infrastructure/web (never the reverse),
     * that application is only depended on by infrastructure/web, and that nothing depends on
     * infrastructure or web.
     */
    @ArchTest
    static final ArchRule layersRespectHexagonalBoundaries = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Web").definedBy("..web..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Web")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Web")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Web").mayNotBeAccessedByAnyLayer();

    /**
     * Verifies that no domain class imports Spring or Jakarta types.
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..");

    /**
     * Verifies that no domain class imports application, infrastructure, or web types.
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..web..");
}
