# Moderne DevCenter

The core recipe building blocks to create Moderne DevCenter dashboards, along with some examples. To customize the [Moderne DevCenter](https://docs.moderne.io/user-documentation/moderne-platform/getting-started/dev-center/) to your needs:

1. Create a new recipe repository using either the [rewrite-recipe-starter](https://docs.moderne.io/user-documentation/workshops/recipe-authoring/#exercise-2-create-and-test-your-own-recipe-module) or your own internal recipe starter template.
2. Add a dependency on [io.moderne.recipe:rewrite-devcenter](https://central.sonatype.com/artifact/io.moderne.recipe/rewrite-devcenter).
3. Copy [devcenter-starter.yml](https://github.com/moderneinc/rewrite-devcenter/blob/main/src/main/resources/META-INF/rewrite/devcenter-starter.yml) to your own repository and start editing.

You may choose to reuse existing core DevCenter card recipes like `LibraryUpgrade` as is, or you may define your own.
