package com.example.carrental.structure;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Keeps the package structure honest as the code grows. The code is organised by feature
 * (fleet, identity, eligibility, booking) with the web layer on top, and dependencies point one way:
 *
 * <pre>
 *   web -> booking -> eligibility -> fleet          booking -> identity
 * </pre>
 *
 * Each feature keeps its entities and repositories in a {@code persistence} sub-package: the tables a feature owns
 * are easy to find, and only that feature queries them.
 *
 * Lower packages never know about higher ones. Where a lower package needs something from a higher one,
 * it defines an interface that the higher one implements ({@code fleet.CapacityGuard}).
 */
class PackageDependencyRulesTest {

    private static final String BASE = "com.example.carrental";

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    void noPackageDependsOnItselfInACircle() {
        // every package is its own slice, so this also covers the sub-packages (booking.availability, eligibility.rules, ...)
        slices().matching(BASE + ".(**)").should().beFreeOfCycles().check(classes);
    }

    @Test
    void lowerPackagesNeverDependOnHigherOnes() {
        forbid("fleet", "identity", "eligibility", "booking", "web", "config");
        forbid("identity", "fleet", "eligibility", "booking", "web", "config");
        forbid("eligibility", "identity", "booking", "web", "config");
        forbid("booking", "web", "config");
    }

    @Test
    void theWebLayerDoesNotTouchPersistenceDirectly() {
        noClasses().that().resideInAPackage(pkg("web"))
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .check(classes);
    }

    @Test
    void repositoriesAreOnlyUsedInsideTheirOwnFeature() {
        // Entities and enums may travel between features (they are the shared vocabulary), but the code that
        // queries the tables belongs to the feature that owns them.
        for (String feature : new String[] {"booking", "fleet", "identity"}) {
            noClasses().that().resideOutsideOfPackage(pkg(feature))
                    .should().dependOnClassesThat(
                            resideInAPackage(pkg(feature + ".persistence")).and(simpleNameEndingWith("Repository")))
                    .check(classes);
        }
    }

    private static void forbid(String from, String... targets) {
        String[] targetPackages = Arrays.stream(targets).map(PackageDependencyRulesTest::pkg).toArray(String[]::new);
        noClasses().that().resideInAPackage(pkg(from))
                .should().dependOnClassesThat().resideInAnyPackage(targetPackages)
                .check(classes);
    }

    private static String pkg(String name) {
        return BASE + "." + name + "..";
    }
}
