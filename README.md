# Moderne DevCenter

The core recipe building blocks to create Moderne DevCenter dashboards, along with some examples. To customize the [Moderne DevCenter](https://docs.moderne.io/user-documentation/moderne-platform/getting-started/dev-center/) to your needs:

1. Create a new recipe repository using either the [rewrite-recipe-starter](https://docs.moderne.io/user-documentation/workshops/recipe-authoring/#exercise-2-create-and-test-your-own-recipe-module) or your own internal recipe starter template.
2. Add a dependency on [io.moderne.recipe:rewrite-devcenter](https://central.sonatype.com/artifact/io.moderne.recipe/rewrite-devcenter).
3. Copy [devcenter-starter.yml](https://github.com/moderneinc/rewrite-devcenter/blob/main/src/main/resources/META-INF/rewrite/devcenter-starter.yml) to your own repository and start editing.

You may choose to reuse existing core DevCenter card recipes like `LibraryUpgrade` as is, or you may define your own.

## Vulnerability dashboards by ecosystem

`io.moderne.devcenter.VulnerabilitiesByEcosystemDevCenter` gives each package ecosystem its own
vulnerability cards, each with a fix action that runs that ecosystem's remediation recipe:

| Ecosystem         | Cards                              | Fix recipe                                                      |
|-------------------|------------------------------------|-----------------------------------------------------------------|
| Java (Maven/Gradle) | Severity, UpgradeDelta, RiskScore | `org.openrewrite.java.dependencies.DependencyVulnerabilityCheck`   |
| npm               | Severity, UpgradeDelta             | `org.openrewrite.nodejs.security.DependencyVulnerabilityCheck`     |
| PyPI              | Severity, UpgradeDelta             | `org.openrewrite.python.dependencies.DependencyVulnerabilityCheck` |
| NuGet             | Severity, UpgradeDelta             | `org.openrewrite.csharp.dependencies.DependencyVulnerabilityCheck` |

The Java cards are `DependencyVulnerabilityCheck` cards with a `cardName`. The npm, PyPI, and NuGet cards are
`EcosystemVulnerabilityCheck` cards: each reads the `VulnerabilityReport` data table written by its ecosystem's
vulnerability check earlier in the same recipe list, so keep the check before its cards when you copy the recipe.
To drop an ecosystem, remove its check and its cards together: cards without their check report every scannable
repository as having no vulnerabilities.
A repository is only reported as having no vulnerabilities if the ecosystem's check could scan it, meaning a
manifest carries the marker the check reads (`NodeResolutionResult` for npm, `PythonResolutionResult` for PyPI,
`MSBuildProject` for NuGet). Without it, for example when the LST build couldn't install a repository's npm
dependencies, the repository is not applicable rather than clean. The card's detail counts each
vulnerability in a package version once, even when the report lists it for several projects.

The npm and PyPI checks run report-only (`maximumUpgradeDelta: none`), so the DevCenter run proposes no changes.
The NuGet check has no report-only mode, so the run also computes the NuGet upgrades it would make; the cards are
unaffected.

Each `EcosystemVulnerabilityCheck` takes optional `cardName`, `fixRecipe`, and `fixableOnly` options:

```yaml
recipeList:
  - org.openrewrite.python.dependencies.DependencyVulnerabilityCheck:
      maximumUpgradeDelta: none
  - io.moderne.devcenter.EcosystemVulnerabilityCheck:
      ecosystem: PyPI
      measure: Severity
      cardName: Python vulnerabilities
      fixRecipe: com.example.FixPythonVulnerabilities
```
