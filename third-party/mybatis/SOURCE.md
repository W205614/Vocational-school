# MyBatis 3.5 compatibility security build

This is a local application-build component, **not an official MyBatis release** and not a Maven publication target.

The project requires the MyBatis 3.5 binary API used by MyBatis-Plus. Directly overriding MyBatis to 3.6 removed the `MetaClass.forClass(Class, ReflectorFactory)` entry point and failed the real database regression. This module retains the 3.5.19 implementation and its existing relocated Javassist, replacing only the embedded OGNL sources with 3.4.16 (CVE-2026-113730 is fixed from 3.4.13).

Inputs are immutable Maven Central source artifacts, SHA-256 verified before extraction:

| Input | SHA-256 |
| --- | --- |
| `org.mybatis:mybatis:3.5.19:jar:sources` | `6fc1152ab0e390adaca8c2b6aab1930d4127beb29e071568514a293d7ab3d7da` |
| `ognl:ognl:3.4.16:jar:sources` | `d3d83f6cb1c0fa861f7b6547db460cfc3d33e53d1d9f26ffe29f107ddb486f63` |

`generate-sources` extracts the upstream sources and applies the original MyBatis package relocation to the new OGNL sources. Do not edit generated files. No old OGNL classes or old OGNL Maven metadata are copied. The new embedded OGNL Maven metadata remains in the resulting JAR so scanners can identify its actual version. Upstream copyright headers and the MyBatis combined `META-INF/LICENSE`/`NOTICE` are retained.

Use normal reactor builds; no manual cache patch or prerequisite `mvn install` is required. Do not deploy this component as a library. Its compatibility must be verified by the actual MyBatis-Plus mapper/database regressions and full application CI, and its resulting JAR/images must be scanned. This recipe does not waive any finding or change scanner thresholds. Reassess it when an upstream release provides both the required binary API and fixed embedded dependency.
