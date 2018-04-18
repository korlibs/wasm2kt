### Summary

This project allows to convert WASM (Web Assembly) from its binary format into Kotlin code.
This allow for example to compile C or C++ libraries and use them in Kotlin common
in any supported target including JVM, JS and Native.

The converter itself is written in kotlin.

### How to use

Generate a Hello World in WASM:

```bash
echo -e "#include <stdio.h>\nint main() { printf(\"hello world\\\n\"); return 0; }" > hello.c
docker run --rm -v $(pwd):/src -t apiaryio/emcc emconfigure emcc hello.c -o hello -O3 -s WASM=1
```

This will generate a `hello.wasm` file.

After that, you can use this project to generate a kotlin executable `kt` file from it:

```bash
echo '#!/usr/bin/env kscript' > hello.kt
cat src/main/kotlin/com/soywiz/wasm/WasmModule.kt >> hello.kt
./wasm2kt.kts hello.wasm >> hello.kt
echo -e "fun main(args: Array<String>) = Module.main(args)" >> hello.kt
chmod +x hello.kt
```

Now you can run it using kscript for example:

```bash
kscript hello.kt
```
