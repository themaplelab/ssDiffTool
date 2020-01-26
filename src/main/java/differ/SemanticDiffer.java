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
import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option; //rm later?

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
	private static String originalRenameSuffix = "Original";
	private static HashMap<SootClass, SootClass> newClassMap = new HashMap<SootClass, SootClass>();
	private static HashMap<SootClass, SootClass> newClassMapReversed = new HashMap<SootClass, SootClass>();
	private static PatchTransformer patchTransformer;
	private static HashMap<SootClass, SootClass> originalToRedefinitionClassMap = new HashMap<SootClass, SootClass>();
	
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

		if(options.hasOption("runRename") && options.getOptionValue("runRename").equals("true")) {
			PackManager.v().getPack("wjtp").add(new Transform("wjtp.renameTransform", createRenameTransformer(options1)));
			System.out.println("First soot has these options: " + options1);
			soot.Main.main(options1.toArray(new String[0]));
			//not sure if this is needed
			PackManager.v().getPack("wjtp").remove("wjtp.renameTransform");
			G.reset();
		}
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTransform", createDiffTransformer()));
		options2.set(1, options.getOptionValue("redefcp")+":.:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/rt.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/jce.jar");
		options2.set(options2.size()-3, "TestForVirt4");
		System.out.println("Second soot has these options: " + options2);
		
        soot.Main.main(options2.toArray(new String[0]));
	}

	private static Transformer createRenameTransformer(List<String> options1){
		return new SceneTransformer() {
			protected void internalTransform(String phaseName, Map options) {
				//not super great option handling... gets the soot cp and gets the dir we know contains the og defs of classes
				System.out.println("In phase 1: these are our access to options: "+ options1.get(1));
				String originalDir = options1.get(1).split("\\:")[2];
				System.out.println("This is the dir to use: "+ originalDir);
				ArrayList<SootClass> allOG = resolveClasses(originalDir);
				Scene.v().getApplicationClasses().clear();
				for(SootClass original : allOG){
					original.rename((original.getName()+originalRenameSuffix));
					Scene.v().getOrAddRefType(original.getType());
					original.setApplicationClass();
				}
				System.err.println("Finished rename phase.");
				System.err.println("----------------------------------------------");
				System.err.println("This is the soot class path atm: "+ Scene.v().getSootClassPath());
				System.err.println("These are all of the classes right now: ");
				System.err.println(Scene.v().getClasses());
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
				ArrayList<SootClass> allRedefs = resolveClasses(options.getOptionValue("redefcp"));
				for(SootClass redef : allRedefs){
					//this is so that the redef classes will also get output'd by soot
					redef.setApplicationClass();
				}

				System.err.println("Classes after redef hello load: ");
				System.err.println(Scene.v().getApplicationClasses());

				
				Scene.v().setSootClassPath(renameResultDir);
				ArrayList<SootClass> allOriginals = resolveClasses(renameResultDir);

				sortClasses(allOriginals, allRedefs);
				System.err.println("Classes map after the sort: "+ originalToRedefinitionClassMap);
				
				SootClass original = allOriginals.get(0);
				SootClass redefinition = allRedefs.get(0);
				if (original != null && redefinition != null){
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


				patchTransformer = new PatchTransformer(newClassMap, newClassMapReversed);

				//System.err.println("FINALSET:");
				//System.err.println(Scene.v().getClasses());

				//TODO find better way to init, some of this wont work under nonequal og:redef ratios
				for(SootClass redef : allRedefs){
					SootClass newClass = new SootClass(redef.getPackageName()+redef.getName()+"NewClass", redef.getModifiers());
					newClass.setSuperclass(redef.getSuperclass());
					newClassMap.put(redef, newClass);
					newClassMapReversed.put(newClass, redef);
				}
				for(SootClass og : originalToRedefinitionClassMap.keySet()){
					diff(og, originalToRedefinitionClassMap.get(og));
				}
				//now? fix all of the method references everywhere, in classes we are outputting
				for(SootClass redef : allRedefs){
					patchTransformer.transformMethodCalls(redef.getMethods());
					//might need to omit the init and clinit on this one?
					patchTransformer.transformMethodCalls(newClassMap.get(redef).getMethods());
				}
				
				//weird hack to get soot/asm to fix all references in the class to align with renaming to OG name
				for(SootClass redef : allRedefs){
					String ogRedefName = redef.getName();
					redef.rename(ogRedefName+"Original");
					redef.rename(ogRedefName);
				}
				for(SootClass newCls : newClassMap.values()){
					Scene.v().addClass(newCls);
					writeNewClass(newCls);
				}
			}
		};
	}

	//not currently responsible for validating that the patch contains at least the classes in the original set
	private static void sortClasses(ArrayList<SootClass> allOriginals, ArrayList<SootClass> allRedefs){
		for(SootClass redef : allRedefs){
			//unfortunately not efficient since we're doing a name compare, but shouldnt be a large set...
			for(SootClass og : allOriginals){
				if((redef.getName()+"Original").equals(og.getName())){
					originalToRedefinitionClassMap.put(og, redef);
					break;
				}
			}
		}
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
		System.err.println("\n##########################################");
		System.err.println("Diff Report for original: " + original.getName() + " compared to redefinition: "+ redefinition.getName());
		System.err.println("---------------------------------------");
		checkFields(original, redefinition);
		System.err.println("---------------------------------------");
		checkMethods(original, redefinition);
		System.err.println("---------------------------------------");
        checkInheritance(original, redefinition);
        System.err.println("##########################################\n");
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
				//do the method stealing as we go
				patchTransformer.stealMethodCalls(redefinition, addedMethods);
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

		} else if(!(redefinition.getSuperclass().getName().equals(original.getSuperclass().getName())) && !(redefinition.getSuperclass().getName()+originalRenameSuffix).equals(original.getSuperclass().getName())) {
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has superclass: " + original.getSuperclassUnsafe() + " and redefinition has superclass: " + redefinition.getSuperclassUnsafe());

		} else if(original.getInterfaceCount() != redefinition.getInterfaceCount()){
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has interfaces: " + original.getInterfaces() + " and redefinition has interfaces: " + redefinition.getInterfaces());
		}else{
			System.err.println("\tNo Inheritance differences!");
		}
	}

	protected static int ownEquivMethodHash(SootMethod method){
		//considers the params, since they would be in a signature, unlike:
		//https://github.com/Sable/soot/blob/master/src/main/java/soot/SootMethod.java#L133
		//basically we treat each definition of an overloaded method as different, while soot would not (for some reason)
		return method.getReturnType().hashCode() * 101 + method.getModifiers() * 17 + method.getName().hashCode() + method.getParameterTypes().hashCode();

	}

	//resolves all of the classes in some dir that defines the patch (== set of classes)
	private static ArrayList<SootClass> resolveClasses(String strdir){
		ArrayList<SootClass> allClasses = new ArrayList<SootClass>();
		try{
			File dir = new File(strdir);
			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File file : directoryListing) {
					if(file.toString().contains("class")){
						//ugly parsing, its the only way tho?
						String classname = file.toString().replaceFirst(strdir , "").replace(".class", "").replaceAll("\\/", "");
						System.out.println("Resolving class: " + classname);
						SootClass resolvedClass = Scene.v().forceResolve(classname, SootClass.BODIES);
						allClasses.add(resolvedClass);
					}
				}
			} else {
				System.out.println("Directory supplied is not sufficient to read.");
			}
		} catch(Exception e){
			System.out.println("Some issue accessing the classes to be renamed: "+ e.getMessage());
		}
		return allClasses;
	}
				
}
