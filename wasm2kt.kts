#!/usr/bin/env kscript

@file:DependsOn("com.soywiz:wasm2kt:0.0.3")
@file:MavenRepository("soywiz-bintray", "https://dl.bintray.com/soywiz/soywiz")

com.soywiz.wasm.Wasm.main(args)

