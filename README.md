# Tensura TNO

This repository contains the source code and resources for the Tensura TNO Minecraft mod.

## Native DLL Status

The DLL reviewed below was bundled with `tensura_tno-1.0.4.5`. Starting with `tensura_tno-1.0.4.6`, `tensura_patch_webview2.dll` has been removed from the distributed mod package.

The original source code for the bundled DLL was deleted a long time ago and is no longer available.

The DLL can be inspected and decompiled with [Dogbolt](https://dogbolt.org/). Dogbolt runs multiple decompilers and makes it easier to compare their output. Please note that decompiled code is reconstructed code and may not exactly match the original source.

A copy of the reviewed historical DLL is located at:

```text
src/main/resources/META-INF/native/win64/tensura_patch_webview2.dll
```

## Security Review

A hash-bound technical security review of the WebView2 integration is available in English:

- [Read the full security report](docs/security/tensura_tno_technical_security_review_en.md)
- [Download the PDF report](docs/security/tensura_tno_technical_security_review_en.pdf)

Reviewed artifact identities:

| Artifact | SHA-256 |
|---|---|
| `tensura_patch_webview2.dll` | `325F0118BD2011630E5864125373D91BABA3FBA49E483D77627DC125A5443715` |
| `tensura_tno-1.0.4.5.jar` | `B756D6789DB339DDAA386DCF14AE759F80DD37C31BEB0440ABDA0B1859D9762B` |

The review found no evidence of malware or Windows-kernel compromise in the exact `1.0.4.5` artifacts. It is retained for transparency as a point-in-time technical assessment, not an absolute security certification. Version `1.0.4.6` was not part of this assessment because it is a later release; the reviewed DLL is no longer included in that distributed mod package. Differently hashed files, future releases, and later changes to remote Wiki content require separate review. The report also documents security-hardening recommendations.
