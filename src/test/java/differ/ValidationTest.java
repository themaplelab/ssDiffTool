
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.io.PrintWriter;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import differ.SemanticDiffer;

public class ValidationTest {

	private static final String prefix = Paths.get("").toAbsolutePath().toString();
	private static final String resources = prefix+"/Mvntest/resources";
	private static final String output = resources +"/adapterOutput"; //for final output
	private static final String renamedDir = resources + "/renamedOriginals"; //for first setup patch adapter output
	private static final String normalCp = prefix + "/target/classes/";
	private static final String redefDir = prefix +"/src/main/java/patch"; //for patch classes
	
	@Test
	public void testRunRename() throws Throwable {
		String mainClass = "testexamples.NoChangeTest";

		//setup stuff
		File adapterOut = new File(output);
		adapterOut.mkdirs();
		File renameOut = new File(renamedDir);
        renameOut.mkdirs();
		File origList = new File(prefix + "/" + mainClass + ".originalclasses.out");
		PrintWriter writer = new PrintWriter(origList);
		writer.println(mainClass);
		writer.close();
		//
		System.out.println("java.home = " + System.getProperty("java.home"));
		String jce = System.getProperty("java.home") + "/lib/jce.jar";
		String rt = System.getProperty("java.home") + "/lib/rt.jar";
		String cp = normalCp + ":" + redefDir + ":" + rt + ":" + jce;
		
		String[] differArgs = {"-cp", cp, "-cp", cp, "-w", "-firstDest", renamedDir, "-altDest", output, "-redefcp", redefDir, "-runRename", "true", "-mainClass", mainClass, "-originalclasslist", prefix + "/" + mainClass + ".originalclasses.out", "Example"};
		System.out.println("Command Line: " + Arrays.toString(differArgs));

		SemanticDiffer.setClasses(Arrays.asList(mainClass));
		try{
		SemanticDiffer.main(differArgs);
		}catch(Exception e){
			System.out.println("in error");
			e.getMessage();
			e.printStackTrace(System.out);
		}
		
		Class<?> clazz = validateClassFile(mainClass);
		//		java.lang.invoke.MethodHandle methodHandle
		//	= (java.lang.invoke.MethodHandle) clazz.getMethod("calcSquare").invoke(null);
		//	assertEquals((double) methodHandle.invoke(16.0), 4.0);
		java.lang.reflect.Method methodHandle = clazz.getMethod("calcSquare", int.class);
		assertEquals(methodHandle.invoke(null, 4), 16);  
		
	}

	//https://github.com/Sable/soot/blob/master/src/test/java/soot/jimple/MethodHandleTest.java#L134
	 private Class<?> validateClassFile(String className) throws MalformedURLException, ClassNotFoundException {
		 // Make sure the classfile is actually valid...
		 URLClassLoader classLoader = new URLClassLoader(new URL[] { new File(output).toURI().toURL() });
		 return classLoader.loadClass(className);
	 }

}
