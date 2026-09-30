# Third-party software in this distribution

`lib/` carries the migration tool and everything it needs to run. Two of those jars are
SwingBridge Emulators' own and two are third-party, redistributed unmodified. **This file is the
attribution those third-party licences require**, and `licenses/APACHE-2.0.txt` beside it is the
licence text Apache-2.0 §4(a) requires a redistributor to hand on.

Versions are deliberately not repeated here — the file names in `lib/` are authoritative, and a
version written twice is a version that goes stale.

| Jar in `lib/` | Project | Licence |
|---|---|---|
| `swingbridge-migration-tool-*.jar` | SwingBridge Emulators | Apache-2.0 — `LICENSE` |
| `swingbridge-migration-annotations-*.jar` | SwingBridge Emulators | Apache-2.0 — `LICENSE` |
| `archunit-*.jar` | [ArchUnit](https://www.archunit.org/), TNG Technology Consulting GmbH | Apache-2.0 — `licenses/APACHE-2.0.txt` |
| `slf4j-api-*.jar` | [SLF4J](https://www.slf4j.org/), QOS.ch | MIT — reproduced below |

A swap-table jar you drop into `lib/` is not covered by this table: each carries its own licence
text in its `META-INF/`. The one most often added, `swingbridge-emulators-*.jar`, is GPLv2 with
the Classpath Exception; the two add-ons are LGPL-2.1 (JCalendar) and BSD (JGoodies Forms).

**The ArchUnit jar carries more than ArchUnit.** It relocates Guava and ASM into
`com.tngtech.archunit.thirdparty`, which is why ArchUnit's own POM declares two licences rather
than one. Guava is Apache-2.0 and is covered by the same `licenses/APACHE-2.0.txt`; ASM is
BSD 3-clause, whose notice is reproduced below because that licence requires a binary
redistribution to carry it.

## ASM — BSD 3-clause

```
ASM: a very small and fast Java bytecode manipulation framework
Copyright (c) 2000-2011 INRIA, France Telecom
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holders nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
THE POSSIBILITY OF SUCH DAMAGE.
```

## SLF4J — MIT

```
Copyright (c) 2004-2022 QOS.ch
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

---

*Adding a dependency to this tool adds a row here. `DistZipIT` fails the build if a jar reaches
`lib/` without one, because an unattributed redistribution is the kind of mistake that ships
quietly and is discovered by someone else.*
