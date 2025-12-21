# Migrating from Other Versions

This guide helps you upgrade between different versions of Encryptable.

---

## Current Version: 1.0.0

This is the initial stable release. No migration needed.

---

## Semantic Versioning Policy

Encryptable follows [Semantic Versioning 2.0.0](https://semver.org/):

- **Patch versions (1.0.x)** - No migration needed, drop-in replacement
- **Minor versions (1.x.0)** - Backward compatible features, or Spring Boot updates that may cause breaking changes unrelated to Encryptable's cryptography
- **Major versions (x.0.0)** - Breaking changes to Encryptable's core API or cryptographic architecture

**Minor version upgrades (1.1.0, 1.2.0, etc.)** may include:
- New features and enhancements to Encryptable
- Spring Boot version updates that could introduce breaking changes to your application (not Encryptable itself)
- These updates are always documented with clear migration notes

**Major version upgrades (2.0.0, 3.0.0, etc.)** will include:
- ✅ Detailed migration guide with step-by-step instructions
- ✅ Deprecation warnings in prior minor versions when possible
- ✅ Code examples showing before/after patterns
- ✅ Automated migration tools when feasible
- ✅ Clear timeline and support policy for older versions

> **Note:** Due to Encryptable's stable cryptographic architecture and Transient-knowledge design, it's highly unlikely that breaking changes significant enough to warrant a major version bump (2.0.0+) will occur.\
> The core framework is intentionally designed for long-term stability.\
> Most evolution will happen through backward-compatible minor releases (1.x.0).

---

## Data Safety

**Important:** Encryptable uses cryptographic addressing and deterministic encryption. When upgrading:

⚠️ **Always backup your MongoDB database before upgrading**

While we strive for backward compatibility, cryptographic systems require extra caution:
- Test upgrades in a staging environment first
- Verify data accessibility after upgrade
- Keep a backup of the previous Encryptable version JAR

### Rollback Procedure

If you need to rollback after an upgrade:

1. Stop your application
2. Restore MongoDB backup (if data was migrated)
3. Revert to previous Encryptable version in `build.gradle.kts`:
   ```kotlin
   implementation("tech.wanion:encryptable-starter:1.0.0") // or your previous version
   ```
4. Rebuild and restart

---

## Version-Specific Migrations

As Encryptable evolves, migration guides for each major/minor version will be documented here.

### Migrating to 1.1.0 (Future)
*Migration guide will be added when 1.1.0 is released*

### Migrating to 2.0.0 (Future)
*Migration guide will be added when 2.0.0 is released*

---

## Stay Informed

- Watch the [GitHub repository](https://github.com/WanionTechnologies/Encryptable) for releases
- Check [Changelog](../CHANGELOG.md) for version-specific changes
- Review [GitHub Releases](https://github.com/WanionTechnologies/Encryptable/releases) for announcements

---

## Migration Support

If you encounter issues upgrading Encryptable:
1. Check the version-specific migration guide above
2. Review the [Changelog](../CHANGELOG.md)
3. Search [existing issues](https://github.com/WanionTechnologies/Encryptable/issues)
4. Open a [new issue](https://github.com/WanionTechnologies/Encryptable/issues/new) with:
   - Current version
   - Target version
   - Error messages or unexpected behavior
   - Minimal reproduction example

---

## Questions?

For migration questions or support, please open an issue on [GitHub](https://github.com/WanionTechnologies/Encryptable/issues).