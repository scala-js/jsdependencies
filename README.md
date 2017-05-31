# sbt-jsdependencies

`sbt-jsdependencies` is an sbt plugin allowing to declare dependencies on JavaScript libraries in Scala.js projects.
Dependencies are based on [WebJars](http://www.webjars.org/).
`sbt-jsdependencies` follows the good old "script style" of JavaScript dependencies, in which JS libraries are simply concatenated together.

`sbt-jsdependencies` is not particularly recommended for new projects.
Most projects should use [`scalajs-bundler`](https://scalacenter.github.io/scalajs-bundler/) instead, which provides saner dependencies through `npm`.
Advantages of `scalajs-bundler` over `sbt-jsdependencies` include:

* Support for transitive dependencies through npm
* Smart bundling with webpack
* Support for [CommonJS modules](https://www.scala-js.org/doc/project/module.html) in Scala.js
* No ambiguity issues related to the same .js file being present several times on the classpath

This repository contains `sbt-jsdependencies` for Scala.js 1.x. In Scala.js
0.6.x, the `jsDependencies` sbt setting is part of the core distribution.
