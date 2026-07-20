# Technical Security Review

## Tensura TNO WebView2 Integration

**Review date:** July 20, 2026

**Report type:** Hash-bound, point-in-time technical assessment

**Overall conclusion:** No malicious or kernel-compromise behavior was identified in the reviewed artifacts. Security hardening is still recommended.

---

## 1. Executive Summary

This review separately examined the following components:

1. The native Windows DLL `tensura_patch_webview2.dll`
2. The NeoForge mod JAR `tensura_tno-1.0.4.5.jar`
3. The Java-to-JNI-to-WebView2 execution path
4. The Chinese Wiki page hosted on Tencent Docs
5. The international Wiki hosted on wiki.gg

The technical evidence does **not** support the allegation that the reviewed DLL is a virus or that it compromises the Windows kernel.

No driver loading, kernel-device access, privilege escalation, process injection, persistence mechanism, operating-system command execution, or command-and-control client was identified. The DLL functions as a JNI bridge between Java/Minecraft and Microsoft WebView2. The DLL embedded in the JAR is byte-for-byte consistent with the separately supplied DLL, as confirmed by SHA-256.

The reviewed DLL received a `0/69` result on VirusTotal. A local Microsoft Defender custom scan performed on July 20, 2026, using product version `4.18.26060.3008` and security-intelligence version `1.455.230.0`, reported no threats in either file. Hybrid Analysis reported `No Specific Threat`; its integrated CrowdStrike Falcon and MetaDefender results were `clean` and `0/24`, respectively. AlienVault OTX showed no associated threat pulses for either reviewed file hash.

However, this report is not an absolute security certification. The architecture still has legitimate security and privacy concerns:

- The Java loader searches several locations for a same-named native DLL, creating a DLL hijacking opportunity.
- The browser configuration accepts arbitrary HTTP or HTTPS URLs.
- WebView2 navigation is not restricted by a strict domain and scheme allowlist.
- The international Wiki loads a large advertising and tracking ecosystem.
- Remote Wiki content can change without any change to the JAR or DLL hash.
- Neither the JAR nor the DLL is digitally signed.

The appropriate professional conclusion is therefore:

> No evidence of malware or kernel compromise was identified in the exact reviewed artifacts. No malicious behavior was identified in the reviewed implementation, but it should be hardened before it is described as fully secure.

---

## 2. Scope and Artifact Identity

### 2.1 Native DLL

- **File name:** `tensura_patch_webview2.dll`
- **SHA-256:** `325F0118BD2011630E5864125373D91BABA3FBA49E483D77627DC125A5443715`
- **SHA-1:** `4F542A7146B1B467BFF4E1B19929B5BD43D567FC`
- **MD5:** `9857133B8ECDB3A200E1352D2677B296`
- **Size:** 92,672 bytes
- **Format:** Native Windows x64 DLL
- **Digital signature:** None
- **PE/COFF linker timestamp:** February 19, 2026 at 14:30:48 UTC. This field is not reliable proof of the actual compilation time.

### 2.2 NeoForge JAR

- **File name:** `tensura_tno-1.0.4.5.jar`
- **SHA-256:** `B756D6789DB339DDAA386DCF14AE759F80DD37C31BEB0440ABDA0B1859D9762B`
- **Size:** 1,319,807 bytes
- **JAR signature:** None
- **File-name version:** `1.0.4.5`
- **Version declared in `neoforge.mods.toml`:** `1.0.4.4`

The version mismatch is not evidence of malicious behavior, but it weakens release traceability and should be corrected.

### 2.3 Embedded DLL Verification

The JAR contains:

`META-INF/native/win64/tensura_patch_webview2.dll`

The embedded file is 92,672 bytes and has the following SHA-256:

`325F0118BD2011630E5864125373D91BABA3FBA49E483D77627DC125A5443715`

This exactly matches the separately supplied DLL.

This report does not apply to any differently sized or differently hashed DLL, including a separately observed file named `tensura_patch_webview2 (1).dll`.

---

## 3. Review Methodology

The assessment used multiple independent evidence sources:

- PE header, section, entropy, import, export, TLS, resource, overlay, and signature inspection
- Native decompilation with Ghidra 12.1.2
- Targeted review of the DLL entry point, every JNI export, registry operations, file operations, runtime linking, and all security-tool matches
- Capability analysis with FLARE capa 9.4.0
- x64 instruction scanning with Capstone
- Decompilation of all 376 Java classes with CFR 0.152
- Whole-JAR scanning for dangerous APIs, operating-system process execution, network clients, external URLs, file writes, and native loading
- JAR archive inspection for duplicate paths, path traversal, encrypted entries, compression-bomb indicators, and embedded executables
- Local Microsoft Defender custom scans
- Public reputation checks using VirusTotal, Hybrid Analysis (including its integrated CrowdStrike Falcon and MetaDefender results), and AlienVault OTX
- Dynamic browser inspection of both Wiki pages, including redirects, scripts, iframes, forms, external resources, console errors, dangerous URI schemes, and download links
- HTTP security-header inspection

The reviewed DLL and JAR were not uploaded to any third-party service during this assessment. Public services were queried only by file hash or URL.

---

## 4. Native DLL Findings

### 4.1 Confirmed Purpose

The DLL exports 16 JNI functions under the following namespace:

`Java_com_sjyueluo_tensura_1patch_client_browser_WebView2BrowserSession_*`

The exports implement:

- WebView2 session creation and closure
- URL loading and current-URL retrieval
- Back, forward, and reload navigation
- Browser-control sizing and positioning
- Windows message-loop processing
- Error reporting

The confirmed business logic performs the following operations:

- Initializes COM
- Locates the installed Microsoft WebView2 Runtime
- Reads Edge and WebView2 registry information
- Dynamically loads `EmbeddedBrowserWebView.dll`
- Creates a `webview2_userdata` directory under the current working directory
- Reads CSS element-removal rules
- Injects JavaScript that removes selected DOM elements
- Optionally prevents new windows by redirecting `window.open` calls into the current page

The DLL contains no hardcoded command-and-control address. URLs are supplied by the Java caller.

### 4.2 DLL Entry Point

During process attachment, the custom `DllMain` logic only calls:

`DisableThreadLibraryCalls`

The remaining entry-point code initializes the MSVC security cookie and the C/C++ runtime. No hidden intrusion, downloader, persistence, or kernel initialization path was identified.

### 4.3 Kernel and Malware Capabilities Not Found

The following indicators were not present:

- `NtLoadDriver` or `ZwLoadDriver`
- `CreateService` or `OpenSCManager`
- `SERVICE_KERNEL_DRIVER`
- `DeviceIoControl`
- `SeLoadDriverPrivilege` or `AdjustTokenPrivileges`
- An `ntoskrnl` dependency
- Embedded `.sys` drivers
- Direct `syscall` or `sysenter` instructions
- `WriteProcessMemory`
- `VirtualAllocEx`
- `CreateRemoteThread`
- APC or thread hijacking
- Registry autostart persistence
- Scheduled-task creation
- PowerShell, CMD, rundll32, regsvr32, or similar command execution

`KERNEL32.dll` is a standard Windows user-mode API library. Its presence does not indicate Windows-kernel compromise or driver execution.

### 4.4 Security-Tool False Positives

FLARE capa classified the function at `0x180009770` as an RC4 PRGA implementation. Ghidra decompilation showed that the function performs an AVX2-optimized comparison of UTF-16 strings and returns an ordering result. It has no encryption key, S-box, key-scheduling algorithm, or encryption/decryption flow. The RC4 classification is a false positive.

File-write matches primarily originated from the generic MSVC `fstream` implementation and its normal `fwrite` support code. They do not demonstrate that the application writes to protected system locations.

The broad `DLL Injection` classification was caused by `LoadLibraryW` and `LoadLibraryExW`. The call context shows that these functions locate and load WebView2 runtime components. They are not used to inject code into another process.

### 4.5 Injected JavaScript

Before local CSS selectors are placed into JavaScript single-quoted strings, the DLL:

- Escapes backslashes
- Escapes single quotation marks
- Removes CR and LF characters

No straightforward arbitrary-JavaScript injection path was identified through normal selector entries.

### 4.6 PE Structure

- Standard section names were present.
- No suspicious high-entropy packed section was identified.
- No valid appended payload or overlay was found.
- No embedded driver was found.
- No active TLS callback payload was present.
- ASLR and DEP-compatible flags were present.

---

## 5. JAR and Java Findings

### 5.1 Archive Structure

- **Entries:** 867
- **Java classes:** 376
- **Duplicate entries:** 0
- **Path-traversal entries:** 0
- **Encrypted entries:** 0
- **Compression-bomb indicator:** None identified
- **Additional embedded native binaries or executable scripts:** None identified
- **Reviewed embedded native binary:** The WebView2 DLL listed in Section 2.3

### 5.2 High-Risk API Scan

The complete decompiled code did not contain:

- `Runtime.exec`
- `ProcessBuilder`
- Operating-system command execution
- PowerShell or CMD invocation
- Raw TCP or UDP socket clients
- A standalone command-and-control client
- Code that downloads and executes an additional program
- Code that dynamically downloads Java classes
- Arbitrary ClassLoader injection

The only direct external HTTP client identified in the decompiled Java code was the text-only fallback Wiki reader implemented by `SimpleBrowserSession`.

The only hardcoded external addresses identified in the reviewed Wiki/browser code were:

- `https://docs.qq.com/aio/p/scjg6678qvr4qq3`
- `https://tensura.wiki.gg`

### 5.3 Native DLL Extraction and Loading

The JAR can extract its embedded DLL to:

`.tensura_tno/native/win64/tensura_patch_webview2.dll`

It then loads the DLL with `System.load`.

Before using the bundled DLL, however, the loader also attempts:

- `System.loadLibrary("tensura_patch_webview2")`
- `tensura_patch_webview2.dll` in the current directory
- `native/webview2/build/Release/tensura_patch_webview2.dll`
- `run/tensura_patch_webview2.dll`
- A same-named DLL in the parent directory

This design creates a real DLL hijacking opportunity. A malicious local program, compromised mod, or attacker with write access to one of those locations could place a same-named DLL that is loaded before the bundled copy.

**Risk rating:** Medium

**Malware evidence:** No. This is a design weakness, not evidence that the reviewed DLL is malicious.

### 5.4 URL and Navigation Controls

The file `config/tensura_tno-browser.properties` can define arbitrary values for `homepage` and `startup.first_open_url`.

The URL-normalization logic:

- Accepts explicit `http://` URLs
- Accepts explicit `https://` URLs
- Adds `https://` when no scheme is provided

The native WebView2 integration does not implement a strict `NavigationStarting` allowlist. Blocking new windows does not prevent:

- Same-window redirects
- Same-window links to external sites
- A modified configuration that points to an arbitrary website
- Redirection after a legitimate site is compromised

**Risk rating:** Medium

### 5.5 Text-Only Fallback Browser

`SimpleBrowserSession` uses Java `URLConnection` to retrieve remote pages. It does not execute webpage JavaScript and removes script, style, and HTML tags before displaying text, which substantially reduces code-execution exposure.

Nevertheless, the implementation has the following weaknesses:

- No strict domain allowlist
- Explicit plain-HTTP support
- Ability to request arbitrary HTTP or HTTPS hosts, including private or loopback addresses
- Default Java redirect handling without validating every redirect target
- `readAllBytes()` is called before the result is truncated to 512 KB, allowing a large or continuous response to consume excessive memory
- No strict Content-Type validation

**Risk rating:** Medium-Low to Medium

### 5.6 Local Files and Persistent Browser Data

The Wiki subsystem writes or creates:

- Browser configuration files
- Browser element-removal rule files
- The DLL extracted from the JAR
- The WebView2 user-data directory
- WebView2-generated cache, cookies, and session data

No write path to Windows driver directories, startup directories, service configuration, or registry autostart locations was identified.

### 5.7 Configuration-File Naming Inconsistency

Java creates:

`config/tensura_tno-browser-elements.txt`

The native DLL refers to:

`config/tensura_patch-browser-elements.txt`

This appears to be an integration or versioning defect rather than malicious behavior. It should be corrected because it complicates configuration and review.

---

## 6. Remote Website Assessment

Website results are point-in-time observations from July 20, 2026. Remote content and third-party resources can change without a new JAR or DLL release.

### 6.1 International Wiki - `https://tensura.wiki.gg`

Positive observations:

- HTTPS was active.
- HSTS was enabled, including subdomains.
- `X-Content-Type-Options: nosniff` was present.
- VirusTotal reported `0/94` for the exact URL.
- Google Safe Browsing, Kaspersky, ESET, Fortinet, URLhaus, and other listed services rated it clean.
- No EXE, DLL, JAR, ZIP, or similar download link was observed on the reviewed page.
- The only special-scheme link found was `javascript:print();`, which invokes page printing.

Risk observations:

- 235 page resources were observed, including 59 script resources.
- The DOM contained 53 external script elements, and none used Subresource Integrity.
- Script and advertising chains involved more than 30 origins.
- Observed services included Google Analytics, Google Tag Manager, DoubleClick, Amazon Ads, Criteo, OpenX, Quantserve, Snigel, Prebid, ID5, LiveRamp, and Permutive.
- No Content-Security-Policy response header or CSP meta element was observed.
- Several advertising, identity-sync, and tracking iframes were present.
- Console warnings and errors were generated by advertising and consent-management scripts.

**Assessment:** No evidence that the site was malicious at the time of review. Privacy exposure and third-party supply-chain exposure are material.

**Risk rating:** Low malicious reputation risk; Medium privacy and supply-chain risk.

### 6.2 Chinese Wiki - Tencent Docs

Reviewed URL:

`https://docs.qq.com/aio/p/scjg6678qvr4qq3`

Observed page title, translated into English:

`New Otherworld Wiki 1.21`

Positive observations:

- HTTPS was active.
- HSTS was enabled, including subdomains.
- `X-Content-Type-Options: nosniff` was present.
- The `docs.qq.com` domain received `0/91` on VirusTotal.
- No dangerous URI scheme or executable-file download link was observed.
- Observed resources were primarily delivered from Tencent-controlled Docs, GTIMG, telemetry, and document-image origins.

Risk observations:

- 190 page resources were observed, including 99 script resources.
- The DOM contained 28 external script elements, with no Subresource Integrity attributes.
- The site's Content Security Policy permits `'unsafe-inline'` and `'unsafe-eval'`.
- The CSP allowlist is broad and includes access to `http://127.0.0.1:*` for several resource categories.
- The document owner can change content without changing the mod artifacts.
- VirusTotal had no existing record for the exact document URL, so only the shared-domain reputation could be verified.

**Assessment:** No evidence that the reviewed Tencent document currently serves malicious content. It remains a large, remotely changeable application that cannot be permanently certified by a local artifact hash.

**Risk rating:** Low malicious reputation risk; Medium-Low to Medium remote-content and platform-script risk.

---

## 7. Antivirus and Threat-Intelligence Results

| Component | Service | Result |
|---|---|---|
| DLL | Microsoft Defender | No threats found |
| DLL | VirusTotal | 0/69 |
| DLL | Hybrid Analysis | No Specific Threat |
| DLL | CrowdStrike Falcon via Hybrid Analysis | Clean |
| DLL | MetaDefender via Hybrid Analysis | 0/24 |
| DLL | AlienVault OTX | 0 threat pulses |
| JAR | Microsoft Defender | No threats found |
| JAR | AlienVault OTX | 0 threat pulses |
| JAR | VirusTotal | No existing public item found |
| International Wiki URL | VirusTotal | 0/94 |
| Tencent Docs domain | VirusTotal | 0/91 |

The Microsoft Defender results above came from local custom scans performed on July 20, 2026, using product version `4.18.26060.3008` and security-intelligence version `1.455.230.0`.

Hybrid Analysis invoked the DLL with `rundll32 ...,#1`, and the sample crashed in that test. As a result, that sandbox did not fully reproduce the actual Minecraft and Java execution path. Its low-confidence `\Device\` string match and broad process observations do not demonstrate driver loading by the DLL.

---

## 8. Risk Summary

| ID | Finding | Severity | Evidence of Malware? |
|---|---|---|---|
| R1 | Same-named DLL search and local DLL hijacking opportunity | Medium | No |
| R2 | No strict WebView2 navigation and scheme allowlist | Medium | No |
| R3 | Large third-party advertising and tracking supply chain on wiki.gg | Medium | No |
| R4 | Remote pages can change independently of artifact hashes | Medium | No |
| R5 | Fallback browser can request arbitrary HTTP/HTTPS and private hosts | Medium-Low to Medium | No |
| R6 | Fallback browser reads the full response before applying its 512 KB display limit | Medium-Low | No |
| R7 | Unsigned JAR and DLL | Medium-Low | No |
| R8 | Persistent WebView2 cookies, cache, and tracking data | Medium-Low | No |
| R9 | JAR file-name version does not match internal metadata | Low | No |
| R10 | Java and native element-rule file names do not match | Low | No |

No high-severity malicious-software behavior was identified. Several engineering issues should nevertheless be addressed before making broad security claims.

---

## 9. Recommended Remediation

### 9.1 Native DLL Loading

1. Remove the initial `System.loadLibrary("tensura_patch_webview2")` search.
2. Remove all relative-path fallback candidates.
3. Load only the DLL extracted from the signed JAR into a verified, application-controlled directory.
4. Extract the DLL to a versioned, non-shared directory with restrictive permissions.
5. Recalculate SHA-256 immediately before `System.load` and compare it with a build-time constant.
6. Authenticode-sign the DLL.

### 9.2 JAR Release Integrity

1. Digitally sign the JAR.
2. Publish SHA-256 hashes for every release.
3. Correct the `1.0.4.5` versus `1.0.4.4` version mismatch.
4. Publish the source and reproducible build instructions.
5. Verify the embedded DLL hash during the build.

### 9.3 WebView2 Hardening

1. Permit HTTPS only.
2. Validate every navigation in a `NavigationStarting` handler.
3. Restrict navigation to the exact approved domains and, where possible, exact paths.
4. Revalidate every HTTP redirect and JavaScript navigation target.
5. Reject `file:`, `data:`, `javascript:`, custom URI schemes, and application-launch protocols.
6. Disable unnecessary downloads, camera, microphone, geolocation, clipboard, and notification permissions.
7. Disable DevTools, external protocol launching, and the default context menu if they are not required.
8. Use an isolated or temporary WebView2 user-data directory and provide a clear-data function.
9. Disclose the advertising and tracking behavior of the international Wiki.

### 9.4 Fallback Browser Hardening

1. Stream the response and stop reading at 512 KB.
2. Reject plain HTTP.
3. Reject loopback, link-local, and RFC1918 private network addresses.
4. Reject non-allowlisted hosts, unexpected ports, and URL user-information fields.
5. Disable automatic redirects or validate every redirect target.
6. Require an expected text Content-Type.

### 9.5 Lowest-Attack-Surface Option

For the strongest practical security posture:

- Remove embedded remote WebView2 content.
- Package a static, build-time-hashed Wiki inside the JAR.
- Alternatively, open an allowlisted HTTPS address in the user's system browser instead of rendering remote content inside the Minecraft process.

This still cannot provide mathematical absolute security, but it significantly reduces browser, remote-script, and supply-chain exposure.

---

## 10. Confidence and Limitations

### Confidence

- **Exact DLL contains no identified malicious or kernel-compromise logic:** High
- **Exact JAR contains no identified command executor or malicious downloader:** High
- **Reviewed websites were not malicious at the time of observation:** Medium-High
- **Future permanent safety of the complete system:** Cannot be proven

### Limitations

- Decompilation does not recover original source code, comments, or every original symbol name.
- The complete modpack, all third-party dependencies, and a live multiplayer environment were not fully behavior-monitored.
- The installed WebView2 Runtime version and patch level depend on the user's computer.
- NeoForge, Minecraft, Java, other mods, and operating-system dependencies were outside the complete-audit scope.
- Remote pages, advertising scripts, and Tencent document content can change at any time.
- This report applies only to the exact hashes listed in Section 2.
- Any change to either file invalidates the hash-bound conclusion and requires a new review.

---

## 11. Formal Statement for External Review

> On July 20, 2026, a static assessment was performed on `tensura_patch_webview2.dll` with SHA-256 `325F0118BD2011630E5864125373D91BABA3FBA49E483D77627DC125A5443715` and `tensura_tno-1.0.4.5.jar` with SHA-256 `B756D6789DB339DDAA386DCF14AE759F80DD37C31BEB0440ABDA0B1859D9762B`. The review included native decompilation, Java call-chain analysis, local antivirus scanning, public threat-intelligence checks, review of the available third-party sandbox record, and a point-in-time dynamic inspection of the two configured Wiki pages. No evidence of virus functionality, driver loading, kernel compromise, privilege escalation, process injection, operating-system command execution, or malicious persistence was identified in the reviewed files. The DLL received a 0/69 VirusTotal result, the international Wiki URL received 0/94, and the Tencent Docs domain received 0/91. A local Microsoft Defender custom scan using product version 4.18.26060.3008 and security-intelligence version 1.455.230.0 reported no threats in either file. The system does retain non-malicious security concerns, including DLL search-path hijacking, unrestricted URL configuration, remote webpage supply-chain exposure, advertising and tracking resources, and unsigned release artifacts. The supported conclusion is therefore that no malicious behavior was found in the reviewed fixed artifacts, not that the system can be guaranteed absolutely secure under all future conditions.

---

## 12. References

- Microsoft, User Mode and Kernel Mode: https://learn.microsoft.com/en-us/windows-hardware/drivers/gettingstarted/user-mode-and-kernel-mode
- Microsoft, WebView2Loader: https://learn.microsoft.com/en-us/microsoft-edge/webview2/how-to/static
- Microsoft, WebView2 Runtime Distribution: https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution
- DLL VirusTotal Report: https://www.virustotal.com/gui/file/325f0118bd2011630e5864125373d91baba3fba49e483d77627dc125a5443715
- DLL Hybrid Analysis Report: https://hybrid-analysis.com/sample/325f0118bd2011630e5864125373d91baba3fba49e483d77627dc125a5443715/6a5e088d1b42a6b05c033933
- International Wiki VirusTotal URL Report: https://www.virustotal.com/gui/url/aHR0cHM6Ly90ZW5zdXJhLndpa2kuZ2cv/detection
- Tencent Docs Domain VirusTotal Report: https://www.virustotal.com/gui/domain/docs.qq.com/detection
