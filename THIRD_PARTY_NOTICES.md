# Third-party notices

This file lists the third-party components inside **our part** of the release APK and gives their copyright
notices and licence texts. Our part is one file in the arm64-v8a split APK, `lib/arm64-v8a/libosrsrl.so`:

- the native library built from `native/` (this repository), which statically links Dear ImGui;
- `host.jar`, the Java plugin host (`host/`, this repository) dexed together with a subset of the RuneLite client,
  the RuneLite API and the client's runtime libraries, embedded byte for byte inside `libosrsrl.so`.

This repository's own code (`native/src`, `host/`, `tools/`) is under the ISC licence in [LICENSE](LICENSE). Some
host classes keep the package and class names of RuneLite, FlatLaf, SLF4J or `com.sun.net.httpserver` classes so
that plugins link against them; they are written for this project, not copied from those projects.

## Not covered: Jagex's Old School RuneScape app

**The release APK also contains Jagex's Old School RuneScape Android app** (build 241.4: `classes*.dex`, the
manifest, resources and assets in the base APK, and `liblibs.hal.system.osclient.so`, `libcrashlytics*.so` and
`libdatastore_shared_counter.so` in the split APK), with the patches `tools/repack.sh` applies. That app and its
content are Jagex Ltd's property. They are **not** covered by this file, by the licences below or by this
repository's ISC licence, and nothing here grants any right to them. Old School RuneScape and RuneScape are
trademarks of Jagex Ltd. This project is not affiliated with or endorsed by Jagex.

Some resources inside `host.jar` come from RuneLite but are derived from Jagex's game (see
"Game-derived resources" below); they are not covered by the licences below either.

## Summary

Versions are the exact inputs of `host/build.sh` (`re/runelite/*.jar`) and `native/CMakeLists.txt`.

| Component | Version | Where | Licence | Copyright |
|---|---|---|---|---|
| Dear ImGui (core + OpenGL3 backend) | 1.91.7 | libosrsrl.so | MIT | Copyright (c) 2014-2025 Omar Cornut |
| stb_rect_pack, stb_textedit, stb_truetype (modified, bundled with Dear ImGui) | 1.01, 1.14, 1.26 | libosrsrl.so | MIT or public domain (Unlicense) | Copyright (c) 2017 Sean Barrett |
| ProggyClean.ttf (Dear ImGui's embedded default font) | - | libosrsrl.so | MIT | Copyright (c) 2004, 2005 Tristan Grimmer |
| LLVM libc++ / libc++abi (Android NDK r27d, linked statically) | NDK r27d | libosrsrl.so | Apache-2.0 WITH LLVM-exception | The LLVM project contributors |
| RuneLite client (`net.runelite:client`, subset) | 1.13.1 | host.jar | BSD-2-Clause | Copyright (c) 2018, Adam <Adam@sigterm.info>, and the RuneLite contributors |
| RuneLite API (`net.runelite:runelite-api`) | 1.13.1 | host.jar | BSD-2-Clause | as above |
| RuneLite HTTP API (`net.runelite.arn:http-api`) | 1.2.25 | host.jar | BSD-2-Clause | as above |
| RuneLite GPU bicubic shader (`bicubic.glsl`) | client 1.13.1 | host.jar | BSD-2-Clause | Copyright (c) 2019 logarrhythmic |
| Hyllian's xBR-lv2 shader (`xbr_lv2_*.glsl`) | client 1.13.1 | host.jar | MIT | Copyright (C) 2011-2016 Hyllian |
| Hybrid UI scaling shader (`hybrid.glsl`) | client 1.13.1 | host.jar | CC0 1.0 | Casey Zwicker (dedicated to the public domain) |
| RuneLite fonts (`runescape*.ttf`) | client 1.13.1 | host.jar | CC BY-NC-SA / CC BY-SA 3.0 | WolfieMario, Bucket (see "Fonts") |
| Google Guice (no_aop) | 4.1.0 | host.jar | Apache-2.0 | Copyright 2006-2016 Google, Inc. |
| Guava | 23.2-jre | host.jar | Apache-2.0 | The Guava Authors |
| Gson | 2.8.5 | host.jar | Apache-2.0 | Google Inc. |
| OkHttp | 3.14.9 | host.jar | Apache-2.0 | Square, Inc. |
| Okio | 1.17.2 | host.jar | Apache-2.0 | Square, Inc. |
| Apache Commons Lang | 3.7 | host.jar | Apache-2.0 | Copyright 2001-2017 The Apache Software Foundation |
| Apache Commons Text | 1.2 | host.jar | Apache-2.0 | Copyright 2001-2017 The Apache Software Foundation |
| javax.inject (JSR-330) | 1 | host.jar | Apache-2.0 | The JSR-330 Expert Group |
| FindBugs JSR-305 annotations (`jsr305`) | 3.0.2 | host.jar | Apache-2.0 (as declared by its POM) | FindBugs / JSR-305 authors |
| Protocol Buffers Java Lite (`protobuf-javalite`) | 3.21.12 | host.jar | BSD-3-Clause | Copyright 2008 Google Inc. |
| SLF4J API | 1.7.25 | host.jar | MIT | QOS.ch |
| JOpt Simple | 5.0.1 | host.jar | MIT | Copyright (c) 2004-2015 Paul R. Holser, Jr. |
| AOP Alliance | 1.0 | host.jar | Public domain | AOP Alliance |

Where each licence text below comes from: the jars' own `META-INF` files where they carry one (Apache-2.0, the
NOTICE files), the shipped source files' headers (RuneLite's BSD-2-Clause and the shaders, Dear ImGui and stb), and
otherwise the same project's licence as distributed with another copy of it on the build machine (protobuf: the
Android SDK build-tools NOTICE; SLF4J: the `META-INF/LICENSE.txt` of a later SLF4J release; JOpt Simple: the JDK's
`legal/` notice for it). Most of the jars bundled here carry no licence file of their own.

Not in the APK, so not listed: Plugin Hub and core plugin jars (pushed separately to the app's `files/plugins/`
folder; each hub jar keeps its own LICENSE), `slf4j-simple`, ASM (used only at build time to rewrite bytecode),
FlatLaf and the other `re/hub-deps` jars (compile-only), OkHttp's public suffix data file (d8 drops jar resources;
only the classes are dexed), and Dear ImGui's demo window (not compiled).

## libosrsrl.so

### Dear ImGui 1.91.7

Includes the OpenGL3 renderer backend (`backends/imgui_impl_opengl3.cpp`), under the same licence.
Source: `native/third_party/imgui/LICENSE.txt`.

```text
The MIT License (MIT)

Copyright (c) 2014-2025 Omar Cornut

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

### stb_rect_pack 1.01, stb_textedit 1.14, stb_truetype 1.26

Slightly modified copies shipped with Dear ImGui (`imstb_*.h`). Each is available under either of two licences;
the text below is from the end of each file.

ALTERNATIVE A - MIT License

```text
Copyright (c) 2017 Sean Barrett
Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:
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

ALTERNATIVE B - Public Domain (www.unlicense.org)

```text
This is free and unencumbered software released into the public domain.
Anyone is free to copy, modify, publish, use, compile, sell, or distribute this
software, either in source code form or as a compiled binary, for any purpose,
commercial or non-commercial, and by any means.
In jurisdictions that recognize copyright laws, the author or authors of this
software dedicate any and all copyright interest in the software to the public
domain. We make this dedication for the benefit of the public at large and to
the detriment of our heirs and successors. We intend this dedication to be an
overt act of relinquishment in perpetuity of all present and future rights to
this software under copyright law.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### ProggyClean.ttf

Embedded in `imgui_draw.cpp` as Dear ImGui's default font. The source states: "Copyright (c) 2004, 2005 Tristan
Grimmer. MIT license (see License.txt in http://www.proggyfonts.net/index.php?menu=download)". MIT licence text:

```text
Copyright (c) 2004, 2005 Tristan Grimmer

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

### LLVM libc++ and libc++abi

The NDK's C++ runtime is linked statically (`c++_static`). It is under the Apache License 2.0 with LLVM Exceptions,
whose exception waives the attribution requirements for object code produced by compiling the library into a
program; it is listed here for completeness.

## host.jar

### RuneLite client 1.13.1 (subset), RuneLite API 1.13.1, RuneLite HTTP API 1.2.25

`host.jar` contains the client jar's classes, except the ones this host replaces with its own classes of the same
name, plus all classes of the API and HTTP API jars, and the client jar's resources. RuneLite's files each carry the
BSD-2-Clause licence with their author's copyright line; the text below is the header of the shipped
`net/runelite/client/plugins/gpu/frag.glsl` (the same text, word for word, heads the other RuneLite shaders in the
jar).

```text
Copyright (c) 2018, Adam <Adam@sigterm.info>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

`net/runelite/client/plugins/gpu/scale/bicubic.glsl` carries the same BSD-2-Clause text with the line
"Copyright (c) 2019 logarrhythmic <https://github.com/logarrhythmic>".

### Hyllian's xBR-lv2 shader

`net/runelite/client/plugins/gpu/scale/xbr_lv2_common.glsl`, `xbr_lv2_frag.glsl`, `xbr_lv2_vert.glsl`:

```text
Copyright (C) 2011-2016 Hyllian - sergiogdb@gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

### Hybrid UI scaling shader

`net/runelite/client/plugins/gpu/scale/hybrid.glsl`:

```text
Written in 2025 by Casey Zwicker <caseypzwicker@gmail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights
to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software.
If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
```

### Fonts

From the client jar's `net/runelite/client/ui/`. Copyright and licence as recorded in each font's name table:

- `runescape.ttf` ("RuneScape Chat '07"): Copyright WolfieMario 2013, based on "RuneScape Chat" by WolfieMario
  (http://fontstruct.com/fontstructors/wolfiemario), built with FontStruct
  (http://fontstruct.com/fontstructions/show/855573). Licence: Creative Commons Attribution Non-commercial Share Alike.
- `runescape_bold.ttf` ("RuneScape Bold Font"): Copyright Bucket 2010, based on "RuneScape Chat Bold 2", "RuneScape
  Chat Bold" and "RuneScape Chat" by WolfieMario, built with FontStruct
  (http://fontstruct.fontshop.com/fontstructions/show/307756). Licence: Creative Commons Attribution Share Alike,
  http://creativecommons.org/licenses/by-sa/3.0/.
- `runescape_small.ttf` ("RuneScape Small"): by WolfieMario, built with FontStruct
  (http://fontstruct.com/fontstructions/show/217362). Licence: Creative Commons Attribution Non-commercial Share
  Alike, http://creativecommons.org/licenses/by-nc-sa/3.0/.

FontStruct is a trademark of FSI FontShop International GmbH. These fonts are redistributed unmodified and free of
charge, as part of a non-commercial release.

### Other client resources

- `net/runelite/client/notification.wav`: the client jar's `notification.txt` says it comes from
  https://github.com/akx/Notifications.
- `net/runelite/client/plugins/gpu/colorblind.glsl` cites the algorithm from "Analysis of Color Blindness" by Onur
  Fidaner, Poliang Lin and Nevran Ozguven; it carries no separate licence.

### Game-derived resources

The client jar's skill icons (`skill_icons/`), status icons (`net/runelite/client/game/1067-*.png`), the bundled
cache scripts (`runelite/12/*`, `runelite/index`) and similar images are taken or derived from Jagex's game. Like the
Jagex app itself, they are not covered by any licence in this file.

### Google Guice 4.1.0 (no_aop), Guava 23.2-jre, Gson 2.8.5, OkHttp 3.14.9, Okio 1.17.2, Apache Commons Lang 3.7, Apache Commons Text 1.2, javax.inject 1, JSR-305 3.0.2

Apache License 2.0 (full text in "Apache License 2.0" below). The jars' own NOTICE files are reproduced under
"NOTICE files". Guava, Gson, OkHttp, Okio, javax.inject and jsr305 carry no NOTICE file.

### Protocol Buffers Java Lite 3.21.12

```text
Copyright 2008 Google Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

Code generated by the Protocol Buffer compiler is owned by the owner
of the input file used when generating it.  This code is not
standalone and requires a support library to be linked with it.  This
support library is itself covered by the above license.
```

### SLF4J API 1.7.25

The 1.7.25 jar carries no licence file; SLF4J is distributed under the MIT licence. Text from a
later SLF4J release's `META-INF/LICENSE.txt`:

```text
Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)
All rights reserved.

Permission is hereby granted, free  of charge, to any person obtaining
a  copy  of this  software  and  associated  documentation files  (the
"Software"), to  deal in  the Software without  restriction, including
without limitation  the rights to  use, copy, modify,  merge, publish,
distribute,  sublicense, and/or sell  copies of  the Software,  and to
permit persons to whom the Software  is furnished to do so, subject to
the following conditions:

The  above  copyright  notice  and  this permission  notice  shall  be
included in all copies or substantial portions of the Software.

THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

### JOpt Simple 5.0.1

MIT licence (the jar's manifest `Bundle-License` and POM). Text as distributed with JOpt Simple in the JDK's
`legal/jdk.internal.opt/jopt-simple.md`:

```text
Copyright (c) 2004-2015 Paul R. Holser, Jr.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
```

### AOP Alliance 1.0

The AOP Alliance interfaces (`org.aopalliance.*`) are in the public domain.

## NOTICE files

### Apache Commons Lang 3.7 (`META-INF/NOTICE.txt`)

```text
Apache Commons Lang
Copyright 2001-2017 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).

This product includes software from the Spring Framework,
under the Apache License 2.0 (see: StringUtils.containsWhitespace())
```

### Apache Commons Text 1.2 (`META-INF/NOTICE.txt`)

```text
Apache Commons Text
Copyright 2001-2017 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).
```

### Google Guice 4.1.0 (`META-INF/NOTICE`)

```text
Google Guice - Core Library
Copyright 2006-2016 Google, Inc.

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).
```

## Apache License 2.0

The text shipped in the Commons Text 1.2 jar (`META-INF/LICENSE.txt`; the Commons Lang and Guice jars carry the same
text).

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
