Generate a Hello World in WASM:

```
echo -e "#include <stdio.h>\nint main() { printf(\"hello world\"); return 0; }" > hello.c
docker run --rm -v $(pwd):/src -t apiaryio/emcc emconfigure emcc hello.c -o hello -O3 -s WASM=1
```

This will generate a `hello.wasm` file.

After that, you can use this project to generate a kotlin executable `kt` file from it.
