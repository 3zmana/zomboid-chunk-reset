# Third-party notices

## pzdataspec

The chunk content decoder (`core/src/main/java/zomboidchunkreset/core/terrain/ChunkContentReader.java`)
follows the description of the Build 42 save format published by the pzdataspec project:
<https://github.com/cff29546/pzdataspec>. No file of that project is included; the decoder is our own
code, checked on real saves. The project's licence:

```
MIT License

Copyright (c) 2026 Min Xiang

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

## Libraries

The packaged application includes OpenJFX (GPL v2 with the Classpath Exception) and sqlite-jdbc
(Apache License 2.0), downloaded by Gradle at build time.
