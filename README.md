Generate a Hello World in WASM:

```
echo -e "#include <stdio.h>\nint main() { printf(\"hello world\\\n\"); return 0; }" > hello.c
docker run --rm -v $(pwd):/src -t apiaryio/emcc emconfigure emcc hello.c -o hello -O3 -s WASM=1
```

This will generate a `hello.wasm` file.

After that, you can use this project to generate a kotlin executable `kt` file from it:

```
echo '#!/usr/bin/env kscript' > hello.kt
cat src/main/kotlin/com/soywiz/wasm/WasmModule.kt >> hello.kt
./wasm2kt.kts hello.wasm >> hello.kt
echo -e "fun main(args: Array<String>) = Module.main(args)" >> hello.kt
chmod +x hello.kt
```

Now you can run it using kscript for example:

```
kscript hello.kt
```
