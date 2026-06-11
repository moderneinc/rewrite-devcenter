# Third-party data notices

## Bundled end-of-life feed

The end-of-life DevCenter cards (`io.moderne.devcenter.eol.*`) ship a bundled snapshot of release
end-of-life data at [`src/main/resources/eol/eol-feed.yaml`](src/main/resources/eol/eol-feed.yaml),
along with the per-ecosystem snapshots under [`samples/feeds`](samples/feeds).

This data is **derived from [endoflife.date](https://endoflife.date)** and has been transformed into
the Moderne EOL feed format (see [`FEED-FORMAT.md`](FEED-FORMAT.md)) by
[`buildSrc/.../GenerateEolFeedTask.java`](buildSrc/src/main/java/io/moderne/devcenter/eol/build/GenerateEolFeedTask.java).
It is a point-in-time snapshot; regenerate it with `./gradlew generateEolFeed`. The synthesized
vendor-neutral `openjdk` runtime product is built from a single JDK vendor's schedule (Eclipse
Temurin by default), recorded in the feed's `metadata.javaRuntimeSource`.

endoflife.date is distributed under the MIT License. The required notice is reproduced below.

```
MIT License

Copyright (c) 2020 endoflife.date contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Source: <https://github.com/endoflife-date/endoflife.date> ·
License: <https://github.com/endoflife-date/endoflife.date/blob/master/LICENSE>
