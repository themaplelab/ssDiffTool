# Patch Adapter
  * a tool for adjusting software patches so that they can be used for hotfixing with a Java agent that uses the Instrumentation API
  * this project depends on [Soot](https://github.com/soot-oss/soot)!


## Standards:
  * currently follows [Instrumentation API](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/Instrumentation.html#redefineClasses-java.lang.instrument.ClassDefinition...-) constraints from `redefineClasses` method in JavaSe 8

## How it works:
  * the tool runs in two phases, where each invokes a new run of Soot
    1) renames the set of original classes and places in to the directory 'renamedOriginals'
    2) Soot loads both the renamed originals and the classes in the patch, then :
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
		* .: current working directory
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


## Testing:
   * tests can be run with: `mvn test`
   * testing requires additional heap, currently set in `pom.xml`, minimum required is 1gb
   * to run only one test: `mvn -Dtest=ValidationTest#testRunFieldAddition test`
   * to expand the testset:
     1) create the **patch version** to use as a test class in `src/main/java/testexamples/`
     2) get the classfile for that, i.e., `mvn compile`
     3) find the classfile for the new patch (`find . -name X.class`), then copy it and its source to `src/main/java/patch/testexamples/'
     4) rename the package for the source in `src/main/java/patch/testexamples/` to `patch.testexamples` so that when this recompiles when rebuilding the project, it does not conflict with original version of same class
     5) now create the corresponding **original version** of that class in `src/main/java/testexamples/`
     6) add corresponding tests for this added setup