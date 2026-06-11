# The Moderne EOL feed format

The end-of-life DevCenter cards in this module match the dependencies and runtimes they detect
against a **Moderne EOL feed**: a vendor-neutral, [package-URL](https://github.com/package-url/purl-spec)-keyed
description of products and their release-cycle end-of-life dates.

The format is deliberately small so that an organization can **author its own feed by hand** — for
internal frameworks, golden base images, or platform SDKs that no public source tracks — or import
one from any source. [endoflife.date](https://endoflife.date) is just one such importer (see
[`buildSrc/.../GenerateEolFeedTask.java`](buildSrc/src/main/java/io/moderne/devcenter/eol/build/GenerateEolFeedTask.java)),
not the schema.

The canonical form is YAML; JSON is also accepted (YAML is a superset). The schema is published at
[`src/main/resources/eol/feed-schema.json`](src/main/resources/eol/feed-schema.json) for editor
validation.

## Shape

```yaml
schemaVersion: 1                       # always 1
metadata:                              # optional, free-form provenance
  source: https://endoflife.date
  generatedAt: 2026-06-09T00:00:00Z
products:
  - name: spring-boot                  # stable slug; used in the `products` allow-list and reports
    label: Spring Boot                 # human-readable name
    coordinates:                       # how a detected artifact/runtime maps to this product
      - pkg:maven/org.springframework.boot
    cycles:
      - cycle: "3.2"                    # release cycle name
        eolDate: 2025-11-23             # ISO-8601, or null/omitted when unknown
        eol: true                       # fallback flag, used only when eolDate is absent
      - cycle: "3.3"
        eolDate: null
        eol: false
```

## Coordinates

A coordinate is a package-URL that maps a detected thing to a product. The matching is coarse on
purpose — one coordinate covers a whole product:

| Coordinate                              | Matches                                                        |
|-----------------------------------------|----------------------------------------------------------------|
| `pkg:maven/<groupId>`                   | any Maven/Gradle artifact in that group (artifact is wildcard) |
| `pkg:npm/<package>`                      | that npm package (`pkg:npm/express`, `pkg:npm/@angular/core`)  |
| `pkg:nuget/<package>`                   | that NuGet package (`pkg:nuget/Newtonsoft.Json`)               |
| `pkg:generic/<runtime>`                 | a language runtime: `openjdk`, `node`, `dotnet`, `dotnetfx`, `python` |
| `pkg:docker/<namespace>/<name>`         | a Docker base image (`pkg:docker/library/node`); official images use the `library` namespace |

A single product may list coordinates of different types (e.g. Node.js carries `pkg:generic/node`
for the runtime, `pkg:docker/library/node` for the base image, and any npm coordinates).

Docker images are matched on the **leading version of the tag** (`node:18.20-alpine` → cycle `18`).
Tags that are not versions — codenames like `debian:bullseye`, or `latest` — cannot be matched.

> **Runtime EOL is vendor-specific.** A `pkg:generic/openjdk` product describes *a particular JDK
> vendor's* support schedule, not a neutral "Java" — vendors differ substantially (e.g. Eclipse
> Temurin supports Java 8 to 2030, well beyond the OpenJDK community). The bundled feed defaults to
> Eclipse Temurin and records the source in `metadata.javaRuntimeSource`; author or regenerate the
> `openjdk` product against the vendor your organization actually runs.

## Cycles

A detected version is matched to the cycle whose name is the **longest dot-boundary prefix** of the
version: `3.2` matches `3.2.4` but not `3.20.0`; `2.7` matches `2.7.18.RELEASE`. A version that
falls in no cycle is ignored (no false positives).

- `eolDate` — the cycle's end-of-life date. When the date is on or before the evaluation date the
  card reports `EndOfLife`; within the approaching window, `EndOfLifeApproaching`; otherwise
  `Supported`.
- `eol` — used only when `eolDate` is absent (e.g. discontinued products with no published date):
  `true` reports `EndOfLife`, `false` reports `Supported`.

## Supplying your own feed

Every card takes an optional `feed` option. It accepts a file path, an `http(s)://` URL, or a
classpath resource; when omitted, the bundled
[`src/main/resources/eol/eol-feed.yaml`](src/main/resources/eol/eol-feed.yaml) is used.

```yaml
recipeList:
  - io.moderne.devcenter.eol.DependencyEndOfLife:
      cardName: JVM dependency end of life
      feed: https://eol.internal.example.com/feed.yaml   # your org's feed
```

A feed is cached so it is parsed (and any URL fetched) at most once per cache window: the bundled
feed and classpath resources are cached for the JVM's life; files and URLs use a TTL (default 1 hour,
override with the `eol.feedTtlMillis` system property) so a long-lived worker eventually sees a
refreshed feed. If an org-supplied feed can't be loaded (bad path/URL, malformed content) the run
does **not** fail — a warning is logged and the bundled feed is used. For reproducibility, prefer
bundling or pre-fetching the feed over fetching a URL at recipe runtime.

To merge internal products with the public data, start from the bundled feed or a generated sample
under [`samples/feeds`](samples/feeds) and add your own product entries.
