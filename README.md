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

# Usage

Add the following line to `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.0")
```

and enable the following plugin on Scala.js projects where you need `jsDependencies`:

```scala
enablePlugins(JSDependenciesPlugin)
```

You can write the following in the `settings` of those projects:

```scala
jsDependencies += "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js"
```

This will make your project depend on the respective WebJar and include a file named `**/2.1.4/jquery.js` in the said WebJar when your project is run or tested.
We are trying to make the semantics of "include" to be as close as possible to writing:

```html
<script type="text/javascript" src="..."></script>
```

All `jsDependencies` and associated metadata (e.g. for ordering) are persisted in a file (called `JS_DEPENDENCIES`) and shipped with the artifact your project publishes.
For example, if you depend on the `scalajs-jquery` package for Scala.js, you do not need to explicitly depend or include `jquery.js`; this mechanism does it for you.

Note: This will **not** dump the JavaScript libraries in the file containing your compiled Scala.js code as this would not work across all JavaScript virtual machines.
However, the Scala.js plugin can generate a separate file that contains all raw JavaScript dependencies (see [below](#packageJSDependencies)).

### Scoping to a Configuration

You may scope `jsDependencies` on a given configuration, just like for normal `libraryDependencies`:

```scala
jsDependencies += "org.webjars" % "jquery" % "2.1.4" / "jquery.js" % "test"
```

### CommonJS name

Some (most?) JavaScript libraries try to adapt the best they can to the environment in which they are being executed.
When they do so, you have to specify explicitly the name under which they are exported in a CommonJS environment (such as Node.js), otherwise they won't work when executed in Node.js.
This is the purpose of the `commonJSName` directive, to be used like this:

```scala
jsDependencies += "org.webjars" % "mustachejs" % "0.8.2" / "mustache.js" commonJSName "Mustache"
```

which essentially translates to a prelude

```javascript
var Mustache = require("mustache.js");
```

when running with Node.js from sbt (with `run`, `test`, etc.).

### Dependency Ordering

Since JavaScript does not have a class loading mechanism, the order in which libraries are loaded may matter.
If this is the case, you can specify a library's dependencies like so:

```scala
jsDependencies += "org.webjars" % "jasmine" % "1.3.1" / "jasmine-html.js" dependsOn "jasmine.js"
```

Note that the dependee must be declared as explicit dependency elsewhere, but not necessarily in this project (for example in a project the current project depends on).

### Local JavaScript Files

If you need to include JavaScript files which are provided in the resources of your project, use:

```scala
jsDependencies += ProvidedJS / "myJSLibrary.js"
```

This will look for `myJSLibrary.js` in the resources and include it.
It is an error if it doesn't exist.
You may use ordering and scoping if you need.

### Write a Dependency File

If you want all JavaScript dependencies to be concatenated to a single file (for easy inclusion into a HTML file for example), you can set:

```scala
skip in packageJSDependencies := false
```

in your project settings. The resulting file in the target folder will have the suffix `-jsdeps.js`.

### Scaladoc

See [the Scaladoc](https://javadoc.io/doc/org.scala-js/jsdependencies-core_2.13/latest/org/scalajs/jsdependencies/core/index.html) for other configuration options.
