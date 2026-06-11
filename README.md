# Moderne DevCenter

The core recipe building blocks to create Moderne DevCenter dashboards, along with some examples. To customize the [Moderne DevCenter](https://docs.moderne.io/user-documentation/moderne-platform/getting-started/dev-center/) to your needs:

1. Create a new recipe repository using either the [rewrite-recipe-starter](https://docs.moderne.io/user-documentation/workshops/recipe-authoring/#exercise-2-create-and-test-your-own-recipe-module) or your own internal recipe starter template.
2. Add a dependency on [io.moderne.recipe:rewrite-devcenter](https://central.sonatype.com/artifact/io.moderne.recipe/rewrite-devcenter).
3. Copy [devcenter-starter.yml](https://github.com/moderneinc/rewrite-devcenter/blob/main/src/main/resources/META-INF/rewrite/devcenter-starter.yml) to your own repository and start editing.

You may choose to reuse existing core DevCenter card recipes like `LibraryUpgrade` as is, or you may define your own.

## End-of-life dashboards

The `io.moderne.devcenter.eol` package provides DevCenter cards that report the **end-of-life (EOL)
status of dependencies and language runtimes** across every repository in your organization, for the
major tech stacks — JVM, npm, NuGet, the Java/Node.js/.NET/Python runtimes, and Docker base images.

Each card buckets every repository by its **worst** status across what it tracks:

| Status                 | Meaning                                                       |
|------------------------|---------------------------------------------------------------|
| `EndOfLife`            | A tracked item's release cycle is past its end-of-life date.  |
| `EndOfLifeApproaching` | An item reaches end of life within the configured window.     |
| `Supported`            | All tracked items are still supported.                        |

### The cards

| Card recipe                                            | Tracks                                                     |
|--------------------------------------------------------|------------------------------------------------------------|
| `io.moderne.devcenter.eol.DependencyEndOfLife`         | Maven & Gradle dependencies (`pkg:maven/<groupId>`)        |
| `io.moderne.devcenter.eol.NpmDependencyEndOfLife`      | npm dependencies in `package.json` (`pkg:npm/<package>`)   |
| `io.moderne.devcenter.eol.NuGetDependencyEndOfLife`    | NuGet `PackageReference`s in `*.csproj` (`pkg:nuget/...`)  |
| `io.moderne.devcenter.eol.RuntimeEndOfLife`            | A language runtime: Java version, Node.js engine, .NET TFM, or Python. Set the `runtime` option (`Java`/`NodeJs`/`DotNet`/`Python`) to track one per card, so the runtime appears in the card title. |
| `io.moderne.devcenter.eol.DockerImageEndOfLife`        | Docker base images in Dockerfile `FROM` (`pkg:docker/...`) |

A shared `io.moderne.devcenter.eol.table.EndOfLifeReport` data table records per-item detail
(ecosystem, kind, name, version, product, cycle, EOL date, status) so you can drill into exactly what
drove a repository's status.

### Ready-to-run DevCenter configurations

Each of these wires its card(s) together with `FindOrganizationStatistics` and `FindCommitters`:

- `io.moderne.devcenter.eol.DevCenterEndOfLife` — every card (JVM, npm, NuGet, a card per language
  runtime, and Docker) in one dashboard.
- `io.moderne.devcenter.eol.DevCenterJvmEndOfLife` / `…NpmEndOfLife` / `…NuGetEndOfLife` /
  `…RuntimeEndOfLife` / `…DockerEndOfLife` — one stack at a time.

Copy one of these recipes into your own repository to customize card names, the approaching window,
the feed, or the fix recipe (below).

### Fix actions (optional remediation)

**Every** card — `DependencyEndOfLife`, `NpmDependencyEndOfLife`, `NuGetDependencyEndOfLife`,
`RuntimeEndOfLife`, and `DockerImageEndOfLife` — takes an optional **`fixRecipe`** option: the id of a
recipe that remediates the card's findings. When set, DevCenter surfaces a one-click **dry run / fix**
action on the card (like the built-in upgrade cards) and tracks remediation progress; when omitted
(and no default applies) the card is **report-only**.

Add `fixRecipe: <recipe id>` to any card in your DevCenter recipe:

```yaml
recipeList:
  - io.moderne.devcenter.eol.DependencyEndOfLife:
      cardName: JVM dependency end of life
      fixRecipe: org.openrewrite.java.dependencies.UpgradeDependencyVersion
  - io.moderne.devcenter.eol.DockerImageEndOfLife:
      cardName: Docker base image end of life
      fixRecipe: <your base-image bump recipe id>
  - io.moderne.devcenter.eol.RuntimeEndOfLife:
      cardName: .NET runtime end of life
      runtime: DotNet
      fixRecipe: OpenRewrite.Recipes.CSharp.Migration.Dotnet.Net10.UpgradeToDotNet10  # overrides the default
```

The recipe is resolved **by id** from the recipes available to DevCenter, so it does not need to be a
dependency of this module — but it must exist in your DevCenter marketplace for the dry run to run.

**Defaults.** For the obvious runtime upgrades a default is provided out of the box (override with
`fixRecipe`); every other card is report-only until you set one:

| Card                                   | Default `fixRecipe`                                                  |
|----------------------------------------|----------------------------------------------------------------------|
| `RuntimeEndOfLife` with `runtime: Java`   | `org.openrewrite.java.migrate.UpgradeToJava25` (current LTS)       |
| `RuntimeEndOfLife` with `runtime: DotNet` | `OpenRewrite.Recipes.CSharp.Migration.Dotnet.Net10.UpgradeToDotNet10` |
| all other cards                        | none — report-only until `fixRecipe` is set                          |

### The EOL feed

Matching data comes from a pluggable **Moderne EOL feed** — a vendor-neutral,
[package-URL](https://github.com/package-url/purl-spec)-keyed YAML/JSON file. A snapshot derived from
[endoflife.date](https://endoflife.date) is bundled (`src/main/resources/eol/eol-feed.yaml`) so the
cards run fully offline; an organization can supply its own feed (for internal frameworks, golden base
images, or platform SDKs) via the `feed` option on any card without rebuilding the recipe. See
[`FEED-FORMAT.md`](FEED-FORMAT.md) for the schema and [`samples/`](samples) for ready-to-edit
per-ecosystem starting points and example report extracts.

Refresh the bundled feed from the live endoflife.date data with `./gradlew generateEolFeed`
(and `./gradlew generateSamples` to also regenerate the `samples/` artifacts). The bundled snapshot
is derived from [endoflife.date](https://endoflife.date) (MIT); see [`NOTICE.md`](NOTICE.md) for
attribution.
