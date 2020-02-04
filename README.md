# ssDiffTool (Soot Semantic DiffTool)
  * a tool for determining the differences between two versions of a class
  * differences are output in terms of Java standard nonpermissible types of redefinition changes, from the JVMTI/Java Agent mechanism
  * this project depends on [Soot](https://github.com/Sable/soot)!


## Standards:
  * currently follows [Instrumentation API](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/Instrumentation.html#redefineClasses-java.lang.instrument.ClassDefinition...-) constraints from `redefineClasses` method in JavaSe 8

## How it works:
  * the tool runs in two phases, where each invokes a new run of Soot
    * first phase: renames the set of original classes and places in to the directory 'renamedOriginals'
	* second phase: Soot loads both the renamed originals and the redefinition classes, then :
	   * SemanticDiffer detects differences of interest between the versions
	   * SemanticDiffer passes points of interest to PatchAdapter to transform the redefinition classes
	   * a set of (possibly transformed) classes are emitted into the directory 'adapterOutput'

## How to build:
  * `mvn clean compile assembly:single` will build the application including dependencies


## How to run:
```
java -cp $cp differ.SemanticDiffer -cp cpplaceholder -w -firstDest renamedOriginals -altDest adapterOutput -f c -redefcp redefcp -runRename [true/false] -mainClass TestRunner -originalcp originalcp Example


```

  * where:
     * $cp is the path to the SemanticDiffer package
	 * cpplaceholder contains the following (in the listed order):
	    * originaldir: the directory of the original version of the class
		* .: current workign directory
		* $latestssbuild: the path to the SemanticDiffer package
		* rt.jar: a path to the jvm's rt
		* jce.jar: a path to the jvms crypto library
		* ${projectdir}test/adapterOutput/: the path to the directory that output of the patch adapter is going into
		* redefcp: the directory of the redefined version of the original class

	 * redefcp is the directory of the redefined version of the original class
     * originalcp is the directory of the original version of the class
	 * Example is any class, as a placeholder to get Soot up and running in a way that it is familiar with
     * TestRunner is the actual entry point class to the application that is being patched
  * the required options are:
    * redefcp, originalcp, mainClass


## How to test:
  * build the application first
  * go to test directory
  * run `./test.sh`