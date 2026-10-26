# Security Policy

## Scope

This policy covers all AgentMeter editions in this repository:
Rider/JetBrains, Windows desktop, Chrome/Edge, Visual Studio Code
and Visual Studio.

## Supported Versions

Security fixes are developed on the main branch and delivered through
updates to the affected edition. Please use its latest published version.

The shared source version is recorded in `gradle.properties`; available
packages and their versions are listed in GitHub Releases. Source updates
and package publication are separate steps. Security backports to older
versions are not guaranteed.

## Reporting a Vulnerability

Please report security vulnerabilities privately through GitHub's
"Report a vulnerability" feature in this repository's Security tab.

If private reporting is unavailable, open an issue requesting a private
security contact. Do not include vulnerability details in that issue.

Never publish passwords, API keys, session tokens, account identifiers
or private provider data in issues, screenshots or attachments.

Include:

- The affected AgentMeter edition and version.
- Your operating system and browser or IDE version.
- Steps to reproduce using redacted or synthetic data.
- The expected behavior, observed behavior and potential impact.

Reports will be reviewed by the maintainer. Additional information may
be requested. Response and fix timelines depend on severity and available
maintainer capacity; no fixed response time is guaranteed.

Please coordinate public disclosure with the maintainer so a fix can
be prepared.

## Other Issues

Quota availability, expired sessions and provider API changes are usually
support issues unless they expose data or bypass security controls.
Report ordinary bugs through GitHub Issues.
