# Claude Code Settings for Oculus Au Naturel

## Project Context

This is a Minecraft Forge mod that provides shader support. It is a fork of Oculus, which is itself a Forge port of the Iris Shaders mod for Fabric.

## Key Information

- **Minecraft Version**: 1.20.1 (primary target)
- **Mod Loader**: Forge
- **Build System**: Gradle
- **Language**: Java 17+
- **License**: LGPL-3.0

## Upstream Projects

1. **Iris Shaders** (Fabric) - https://github.com/IrisShaders/Iris
2. **Oculus** (Forge port) - https://github.com/Asek3/Oculus

## Development Notes

- Uses Mixin for bytecode modification
- Requires Embeddium (Sodium port) as a dependency
- Has special compatibility code for Distant Horizons mod
- Shader packs follow OptiFine/ShadersMod format

## Common Tasks

### Building
```bash
./gradlew build
```

### Testing in Development
```bash
./gradlew runClient
```

## Code Style

- Follow existing code conventions in the codebase
- Preserve original copyright headers when modifying files
- Use descriptive commit messages
