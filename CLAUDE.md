# Oculus Au Naturel - Project Instructions

## Project Overview

This is a fork of Oculus (Forge port of Iris Shaders) customized for the Au Naturel modpack. It provides shader support for Minecraft Forge with enhanced Distant Horizons compatibility.

## Build Environment

Java 17+ is required for building. Standard Gradle build:

```bash
./gradlew build
```

## Project Structure

- `src/main/java/net/irisshaders/iris/` - Main Iris/Oculus code
- `src/main/java/net/irisshaders/batchedentityrendering/` - Entity batching system
- `src/main/java/kroppeb/stareval/` - Expression parser for shader uniforms
- `src/main/resources/` - Mixins and assets
- `glsl-relocated/` - GLSL shader utilities

## Key Components

- **Iris.java** - Main mod entry point
- **DHCompat.java** - Distant Horizons compatibility layer
- **PipelineManager.java** - Shader pipeline management
- **ShaderPack.java** - Shader pack loading and parsing

## Dependencies

- Embeddium (Sodium port for Forge) - Required
- Distant Horizons - Optional, enhanced compatibility

## License

LGPL-3.0 - See LICENSE file
