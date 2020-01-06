package differ;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;

import soot.G;
import soot.Main;
import soot.PackManager;
import soot.Scene;
import soot.SourceLocator;
import soot.Transform;
import soot.Transformer;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.util.Chain;
import soot.jimple.JasminClass;
import soot.options.Options;
import soot.util.JasminOutputStream;

public class SemanticDiffer{


	private static CommandLine options;
	private static String renameResultDir = ".";
	private static SootClass newClass;
	private static PatchTransformer patchTransformer;
	
	public static void main(String[] args) throws ParseException {

		//doesnt die on unknown options, will pass them to soot
		RelaxedParser parser = new RelaxedParser();
		options = parser.parse(new SemanticOptions(), args);
		List<String> options1 = new ArrayList<String>();
		List<String> options2 = new ArrayList<String>();
		
		if(options.hasOption("altDest") && !options.hasOption("firstDest")) {
			System.out.println("Using output dir for both soot runs: "+ options.getOptionValue("altDest"));
			parser.getLeftovers(options2);
			options1.add("-d");
			options1.add(options.getOptionValue("altDest"));
			renameResultDir = options.getOptionValue("altDest");
		}else if(!options.hasOption("altDest") && options.hasOption("firstDest")){
			System.out.println("Using output dir for both soot runs: "+ options.getOptionValue("firstDest"));
			parser.getLeftovers(options1);
			options1.add("-d");
            options1.add(options.getOptionValue("firstDest"));
			renameResultDir = options.getOptionValue("firstDest");
		} else if(options.hasOption("altDest") && options.hasOption("firstDest")){
			parser.getLeftovers(options1);
			options1.add("-d");
            options1.add(options.getOptionValue("firstDest"));
			renameResultDir = options.getOptionValue("firstDest");
			parser.getLeftovers(options2);
            options2.add("-d");
            options2.add(options.getOptionValue("altDest"));
		}
		
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.renameTransform", createRenameTransformer()));
		System.out.println("First soot has these options: " + options1);
		soot.Main.main(options1.toArray(new String[0]));
		//not sure if this is needed
		PackManager.v().getPack("wjtp").remove("wjtp.renameTransform");
		G.reset();
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTransform", createDiffTransformer()));
		options2.set(1, options.getOptionValue("redefcp")+":.:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/rt.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/jce.jar");
		options2.set(options2.size()-3, "Test");
		System.out.println("Second soot has these options: " + options2);
		
        soot.Main.main(options2.toArray(new String[0]));
	}

	private static Transformer createRenameTransformer(){
		return new SceneTransformer() {
			protected void internalTransform(String phaseName, Map options) {
				SootClass original = Scene.v().forceResolve("Hello", SootClass.BODIES);
				original.rename("HelloOriginal");
				Scene.v().getApplicationClasses().clear();
				original.setApplicationClass();
				System.err.println("Finished rename phase, with class: "+ original);
				System.err.println("----------------------------------------------");
			}
		};
	}

	private static Transformer createDiffTransformer(){
		return new SceneTransformer() {
			protected void internalTransform(String phaseName, Map originalOptions) {
				System.err.println("SCENE2: "+ Scene.v());
				Scene.v().getApplicationClasses().clear();
				System.err.println("Initial classes: ");
				System.err.println(Scene.v().getApplicationClasses());
				//Scene.v().printNameToClass();
				System.err.println("Initial classes END ");
				
				Scene.v().setSootClassPath(options.getOptionValue("redefcp"));
				SootClass redefinition = Scene.v().forceResolve("Hello", SootClass.BODIES);
				if (redefinition != null){
					redefinition.setApplicationClass();
				}
				else{
					System.err.println("fail to find redefinition hello");
				}

				System.err.println("Classes after redef hello load: ");
				System.err.println(Scene.v().getApplicationClasses());

				Scene.v().setSootClassPath(renameResultDir);
				SootClass original = Scene.v().forceResolve("HelloOriginal", SootClass.BODIES);

				if (original != null){
					System.err.println("Resulting classes: ");
					System.err.println(Scene.v().getApplicationClasses());
					
					System.err.println("CHECKING if they are the same references: ");
					System.err.println(original);
					System.err.println(redefinition);
					System.err.println(original.equals(redefinition));
					//Scene.v().printNameToClass();
					System.err.println("Original resolving level: "+ original.resolvingLevel());
					System.err.println("redefinition resolving level: "+ redefinition.resolvingLevel());
				}

				System.err.println("FINALSET:");
				System.err.println(Scene.v().getClasses());

				//TODO find better way to init
				newClass = new SootClass(redefinition.getPackageName()+"newClass", redefinition.getModifiers());
                newClass.setSuperclass(redefinition.getSuperclass());
				patchTransformer = new PatchTransformer(newClass);
				
				diff(original, redefinition);
				//weird hack to get soot/asm to fix all references in the class to align with renaming to OG name
				redefinition.rename("HelloOriginal");
				redefinition.rename("Hello");
				if(newClass != null){
					//this should get it to also be output'ed, if it exists
					//newClass.setApplicationClass();
					Scene.v().addClass(newClass);
					writeNewClass(newClass);
				}
			}
		};
	}

	//thank you tutorial: https://www.sable.mcgill.ca/soot/tutorial/createclass/
	private static void writeNewClass(SootClass newClass){
		try{
			String fileName = SourceLocator.v().getFileNameFor(newClass, Options.output_format_class);
			OutputStream streamOut = new JasminOutputStream(new FileOutputStream(fileName));
			PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(streamOut));
			
			JasminClass jasminClass = new JasminClass(newClass);
			jasminClass.print(writerOut);
			writerOut.flush();
			streamOut.close();
		}catch (IOException e){
			System.out.println("Could not write a transformer redefinition file!");
			e.printStackTrace();
		}
		
	}

	private static void diff(SootClass original, SootClass redefinition){
		System.err.println("---------------------------------------");
		System.err.println("Diff Report:");
		System.err.println("---------------------------------------");
		checkFields(original, redefinition);
		System.err.println("---------------------------------------");
		checkMethods(original, redefinition);
		System.err.println("---------------------------------------");
        checkInheritance(original, redefinition);
        System.err.println("---------------------------------------");
	}
	
	private static void checkFields(SootClass original, SootClass redefinition){
		List<SootField> removedFields = new ArrayList<SootField>();
		List<SootField> addedFields = new ArrayList<SootField>();
		HashMap<SootField, SootField> originalToRedefinitionMap = new HashMap<SootField, SootField>();

		//to avoid recomputing the equivalence checks for all original fields
		HashMap<Integer, SootField> originalHashFields = new HashMap<Integer, SootField>(); 
		HashMap<Integer, SootField> redefinitionHashFields = new HashMap<Integer, SootField>();

		for(SootField field : original.getFields()){
			originalHashFields.put(new Integer(field.equivHashCode()), field);
		}

		for(SootField field : redefinition.getFields()){
				boolean matched = false;
				Integer hash = new Integer(field.equivHashCode());
				redefinitionHashFields.put(hash, field);
				if(!originalHashFields.containsKey(hash)) {
					//modified pre-existing field
					for(SootField originalField : original.getFields()){

						boolean sameType = originalField.getType().toString().equals(field.getType().toString());
						boolean sameName = originalField.getName().equals(field.getName());
						boolean sameModifiers = originalField.getModifiers() == field.getModifiers();

						if(sameType && sameName) {
							//modified modifiers
							matched = true;
							originalToRedefinitionMap.put(originalField, field);

							System.err.println("\t The following field has had modifiers altered: \n");
							System.err.println(originalField.getDeclaration() + "  --->  " + field.getDeclaration());
						} else if( sameType &&  sameModifiers){
							matched = true;
							originalToRedefinitionMap.put(originalField, field);

							System.err.println("\t The following field has had its name altered: \n");
                            System.err.println(originalField.getDeclaration() + "  --->  " + field.getDeclaration());
						} else if( sameModifiers && sameName){
							matched = true;
							originalToRedefinitionMap.put(originalField, field);

							System.err.println("\t The following field has had its type altered: \n");
                            System.err.println(originalField.getDeclaration() + "  --->  " + field.getDeclaration());
							
						}
						
					}
					if(!matched){
						//added field means at least two (or even all three) of the above id factors are different
						addedFields.add(field);
					}
				}
			}
			for(SootField field : original.getFields()){
				if(!originalToRedefinitionMap.containsKey(field) && !redefinitionHashFields.containsKey(new Integer(field.equivHashCode()))) {
					removedFields.add(field);
				}
			}

			if(addedFields.size() != 0){	
				System.err.println("\t Field(s) have been added.");
				System.err.println(addedFields);
				patchTransformer.transformFields(redefinition, addedFields);
			}else if(removedFields.size() != 0){
				System.err.println("\tField(s) has been removed");
				System.err.println(removedFields);
				for(SootField f : removedFields){
					//silly thing to have this flag                                                                            
					f.setDeclared(false);
					redefinition.addField(f);
                }
			}else if (originalToRedefinitionMap.size() == 0){
				System.err.println("\tNo Field differences!");
			}
			
	}

	private static void checkMethods(SootClass original, SootClass redefinition){
		List<SootMethod> removedMethods = new ArrayList<SootMethod>();
		List<SootMethod> addedMethods = new ArrayList<SootMethod>();
		HashMap<SootMethod, SootMethod> originalToRedefinitionMap = new HashMap<SootMethod, SootMethod>();

		//to avoid recomputing the equivalence checks for all original methods
		HashMap<Integer, SootMethod> originalHashMethods = new HashMap<Integer, SootMethod>(); 
		HashMap<Integer, SootMethod> redefinitionHashMethods = new HashMap<Integer, SootMethod>();

		for(SootMethod method : original.getMethods()){
			originalHashMethods.put(new Integer(ownEquivMethodHash(method)), method);
		}

		for(SootMethod method : redefinition.getMethods()){
			
				boolean matched = false;
				Integer hash = new Integer(ownEquivMethodHash(method));
				redefinitionHashMethods.put(hash, method);
				if(!originalHashMethods.containsKey(hash)){ 
					//modified pre-existing method
					for(SootMethod originalMethod : original.getMethods()){

						//if there is an exact match for this original item, dont bother to ask if it matches a transformed item
						if(!redefinitionHashMethods.containsKey(ownEquivMethodHash(originalMethod))){

						boolean sameType = originalMethod.getReturnType().toString().equals(method.getReturnType().toString());
						boolean sameName = originalMethod.getName().equals(method.getName());
						boolean sameModifiers = originalMethod.getModifiers() == method.getModifiers();
						boolean sameParameters = originalMethod.getParameterTypes().equals(method.getParameterTypes());
																							
						
						if(sameType && sameName && sameParameters) {
							//modified modifiers
							matched = true;
							originalToRedefinitionMap.put(originalMethod, method);

							System.err.println("\t The following method has had modifiers altered: \n");
							System.err.println(originalMethod.getDeclaration() + "  --->  " + method.getDeclaration());
						} else if(sameType && sameModifiers && sameParameters){
							//modified name, actually NOT SURE
							matched = true;
							originalToRedefinitionMap.put(originalMethod, method);

							System.err.println("\t The following method has had its name altered: \n");
                            System.err.println(originalMethod.getDeclaration() + "  --->  " + method.getDeclaration());
						} else if(sameModifiers && sameName && sameParameters){
							//modified return type
							matched = true;
							originalToRedefinitionMap.put(originalMethod, method);

							System.err.println("\t The following method has had its type altered: \n");
                            System.err.println(originalMethod.getDeclaration() + "  --->  " + method.getDeclaration());
							
						}
					}	
					}
					if(!matched){
						//added method means at least two (or even all three) of the above id factors are different
						addedMethods.add(method);
					}
				}
			}
			for(SootMethod method : original.getMethods()){
				if(!originalToRedefinitionMap.containsKey(method) && !redefinitionHashMethods.containsKey(new Integer(ownEquivMethodHash(method)))) {
					removedMethods.add(method);
				}
			}

			if(addedMethods.size() != 0){	
				System.err.println("\t Method(s) have been added.");
				System.err.println(addedMethods);
				patchTransformer.transformMethodCalls(redefinition, addedMethods);
			}else if(removedMethods.size() != 0){
				System.err.println("\tMethod(s) has been removed");
				System.err.println(removedMethods);
				for(SootMethod m : removedMethods){
					//silly thing to have this flag
					m.setDeclared(false);
					redefinition.addMethod(m);
				}
			}else if (originalToRedefinitionMap.size() == 0){
				System.err.println("\tNo Method differences!");
			}
			
	}
	
	private static void checkInheritance(SootClass original, SootClass redefinition){
		boolean originalHasSuper = original.hasSuperclass();
		boolean redefinitionHasSuper = redefinition.hasSuperclass();
		if((originalHasSuper && !redefinitionHasSuper) || (!originalHasSuper  && redefinitionHasSuper)) {
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has superclass: " + original.getSuperclassUnsafe() + " and redefinition has superclass: " + redefinition.getSuperclassUnsafe());

		} else if(!redefinition.getSuperclass().getName().equals(original.getSuperclass().getName())) {
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has superclass: " + original.getSuperclassUnsafe() + " and redefinition has superclass: " + redefinition.getSuperclassUnsafe());

		} else if(original.getInterfaceCount() != redefinition.getInterfaceCount()){
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has interfaces: " + original.getInterfaces() + " and redefinition has interfaces: " + redefinition.getInterfaces());
		}else{
			System.err.println("\tNo Inheritance differences!");
		}
	}

	private static int ownEquivMethodHash(SootMethod method){
		//considers the params, since they would be in a signature, unlike:
		//https://github.com/Sable/soot/blob/master/src/main/java/soot/SootMethod.java#L133
		return method.getReturnType().hashCode() * 101 + method.getModifiers() * 17 + method.getName().hashCode() + method.getParameterTypes().hashCode();
		
	}
		
}
