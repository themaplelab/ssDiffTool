package differ;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option; //rm later?

import soot.Modifier;
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
import soot.asm.CacheClassProvider;

public class SemanticDiffer{


	private static CommandLine options;
	private static String renameResultDir = ".";
	private static String originalRenameSuffix = "Original";
	private static HashMap<SootClass, SootClass> newClassMap = new HashMap<SootClass, SootClass>();
	private static HashMap<SootClass, SootClass> newClassMapReversed = new HashMap<SootClass, SootClass>();
	private static PatchTransformer patchTransformer;
	private static HashMap<SootClass, SootClass> originalToRedefinitionClassMap = new HashMap<SootClass, SootClass>();
	private static List<String> allEmittedClassesRedefs = new ArrayList<String>();
	private static List<String> allEmittedClassesHostClasses = new ArrayList<String>();

	private static HashMap<SootClass, List<CheckSummary>> redefToDiffSummary = new HashMap<SootClass, List<CheckSummary>>();
	
	public static void main(String[] args) throws ParseException {

		//doesnt die on unknown options, will pass them to soot
		RelaxedParser parser = new RelaxedParser();
		options = parser.parse(new SemanticOptions(), args);
		List<String> options1 = new ArrayList<String>();
		List<String> options2 = new ArrayList<String>();
		List<String> options1final = new ArrayList<String>();
		List<String> options2final = new ArrayList<String>();
		
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
			PackManager.v().getPack("wjtp").add(new Transform("wjtp.renameTransform", createRenameTransformer(options1, options.getOptionValue("originalclasslist"))));
			for(int i =2; i < options1.size(); i++){
				options1final.add(options1.get(i));
			}

			//Options.v().set_src_prec(Options.v().src_prec_cache);    
			System.out.println("First soot has these options: " + options1final);
			soot.Main.main(options1final.toArray(new String[0]));
			//not sure if this is needed
			PackManager.v().getPack("wjtp").remove("wjtp.renameTransform");
			G.reset();
		}
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTransform", createDiffTransformer()));
		//options2.set(1, options.getOptionValue("redefcp")+ ":" +options1.get(1));


		//		for(int i =2; i < options2.size(); i++){
		//	options2final.add(options2.get(i));
		//}
		options2final.add("-w");
		options2final.add( options.getOptionValue("mainClass"));
		//options2.set(options2.size()-3,  options.getOptionValue("mainClass"));

		//this is so that when using src_cache we can get all classes from scc EXCEPT the app class
		/*List<String> classpathForSootTwo = Arrays.asList(options1.get(1).split(":"));
		System.out.println("SSDIFF list of cp is: "+ classpathForSootTwo);
		classpathForSootTwo.set(0, "");
		String choppedClasspath = String.join(":", classpathForSootTwo);
		System.out.println("SSDIFF choppedClasspath: "+ choppedClasspath);
		Options.v().set_soot_classpath(options.getOptionValue("redefcp")+ ":" +choppedClasspath);
		*/
		Options.v().set_soot_classpath(options.getOptionValue("redefcp")+ ":"+options1.get(1));

		Options.v().set_output_dir(options.getOptionValue("altDest"));
		Options.v().set_src_prec(Options.v().src_prec_cache);
		Options.v().set_allow_phantom_refs(true);
		Options.v().setPhaseOption("cg", "all-reachable:true");
		System.out.println("Second soot has these options: " + options2final);
		System.out.println("Second soot has this classpath (newvs): "+ Scene.v().getSootClassPath());
		CacheClassProvider.setTestClassUrl(""); //this is a bad way to set this
		//Scene.v().getApplicationClasses().clear();
		//also want to clear the nametoclass map of the main class entry
		//Scene.v().removeClass(Scene.v().getSootClassUnsafe(options.getOptionValue("mainClass")));
        soot.Main.main(options2final.toArray(new String[0]));
	}

	private static Transformer createRenameTransformer(List<String> options1, String originalList){
		return new SceneTransformer() {
			protected void internalTransform(String phaseName, Map options) {
				//not super great option handling... gets the soot cp and gets the dir we know contains the og defs of classes
				System.out.println("In phase 1: these are our access to options: "+ options1.get(1));
				System.out.println("This is the classlist file to use: "+ originalList);
				ArrayList<SootClass> allOG = gatherClassesFromFile(originalList);
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

				List<String> mainClassPackageNameComponents = new ArrayList<String>();
				String[] mainClassPackageNameComponentsAll = options.getOptionValue("mainClass").split("\\.");
				
				for(int i =0; i< mainClassPackageNameComponentsAll.length-1; i++){
					mainClassPackageNameComponents.add(mainClassPackageNameComponentsAll[i]);
				}
				String mainClassPackageName = String.join("/", mainClassPackageNameComponents) + "/";
				
				System.err.println("SCENE2: "+ Scene.v());
				Scene.v().getApplicationClasses().clear();
				System.err.println("Initial classes: ");
				System.err.println(Scene.v().getApplicationClasses());
				//Scene.v().printNameToClass();
				System.err.println("Initial classes END ");

				Scene.v().setSootClassPath(options.getOptionValue("redefcp")+":"+Scene.v().getSootClassPath());
				System.err.println("classpath before redef classes load: "+ Scene.v().getSootClassPath());
				ArrayList<SootClass> allRedefs = gatherClassesFromDir(options.getOptionValue("redefcp")+"/"+mainClassPackageName, mainClassPackageName.replace("/", "."));
				for(SootClass redef : allRedefs){
					//this is so that the redef classes will also get output'd by soot
					redef.setApplicationClass();
				}

				System.err.println("Classes after redef hello load: ");
				System.err.println(Scene.v().getApplicationClasses());

				
				Scene.v().setSootClassPath(renameResultDir+":"+Scene.v().getSootClassPath());
				System.err.println("classpath before renamed classes load: "+ Scene.v().getSootClassPath());
				ArrayList<SootClass> allOriginals = gatherClassesFromDir(renameResultDir + "/"+mainClassPackageName, mainClassPackageName.replace("/", "."));

				//for(SootClass og : allOriginals){
					//this is so the classes dont get set as phantom?
				//	og.setApplicationClass();
				//}
				
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
					System.err.println("Original is phantom: "+ original.isPhantomClass());
					System.err.println("Redef is phantom: "+ redefinition.isPhantomClass());
				}


				patchTransformer = new PatchTransformer(newClassMap, newClassMapReversed);

				//System.err.println("FINALSET:");
				//System.err.println(Scene.v().getClasses());

				//TODO find better way to init, some of this wont work under nonequal og:redef ratios
				for(SootClass redef : allRedefs){
					System.out.println("Creating a new class with the following name: "+ redef.getName()+"NewClass");
					SootClass newClass = new SootClass(redef.getName()+"NewClass", redef.getModifiers());
					if(redef.hasSuperclass()){
						newClass.setSuperclass(redef.getSuperclass());
					}
					newClassMap.put(redef, newClass);
					newClassMapReversed.put(newClass, redef);
					PatchTransformer.createInitializer(newClass);
					//all newclasses will have maps to track who they belong with
					PatchTransformer.buildHostMaps(newClass, "originalToHost");
					PatchTransformer.buildHostMaps(newClass, "hostToOriginal");
					PatchTransformer.setupRedefInit(newClass, redef);
				}
				for(SootClass og : originalToRedefinitionClassMap.keySet()){
					diff(og, originalToRedefinitionClassMap.get(og));
				}
				//now? fix all of the method references everywhere, in classes we are outputting
				for(SootClass redef : allRedefs){
					//patchTransformer.transformMethodCalls(redef.getMethods(), redefToDiffSummary.get(redef)[0].addedList);
					patchTransformer.transformMethodCalls(redef.getMethods(), true);
					//might need to omit the init and clinit on this one?
					patchTransformer.transformMethodCalls(newClassMap.get(redef).getMethods(), false);
				}
				
				//weird hack to get soot/asm to fix all references in the class to align with renaming to OG name
				for(SootClass redef : allRedefs){
					String ogRedefName = redef.getName();
					redef.rename(ogRedefName+"Original");
					redef.rename(ogRedefName);
					allEmittedClassesRedefs.add(redef.getName());
				}
				for(SootClass newCls : newClassMap.values()){
					Scene.v().addClass(newCls);
					allEmittedClassesHostClasses.add(newCls.getName());
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
		CheckSummary fieldSummary = checkFields(original, redefinition);
		CheckSummary methodSummary = checkMethods(original, redefinition);
		List<CheckSummary> summaryList = new ArrayList<CheckSummary>();
		summaryList.add(methodSummary);
		summaryList.add(fieldSummary);
		redefToDiffSummary.put(redefinition, summaryList);
		System.err.println("---------------------------------------");
        checkInheritance(original, redefinition);
		System.err.println("---------------------------------------");
		finishFieldSummary(fieldSummary, redefinition, methodSummary);
		System.err.println("---------------------------------------");
		finishMethodSummary(methodSummary, redefinition);
		System.err.println("---------------------------------------");
        System.err.println("##########################################\n");
	}
	
	private static CheckSummary checkFields(SootClass original, SootClass redefinition){
		HashMap<SootField, SootField> originalToRedefinitionMap = new HashMap<SootField, SootField>();
		CheckSummary fieldSummary = new CheckSummary<SootField>();
		
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
						fieldSummary.addAddedItem(field);
					}
				}
			}
			for(SootField field : original.getFields()){
				if(!originalToRedefinitionMap.containsKey(field) && !redefinitionHashFields.containsKey(new Integer(field.equivHashCode()))) {
					fieldSummary.addRemovedItem(field);
				}
			}

			if (originalToRedefinitionMap.size() == 0){
				fieldSummary.changes = false;
			}else{
				fieldSummary.changes = true;
			}
			return fieldSummary;

	}
	private static void finishFieldSummary(CheckSummary fieldSummary, SootClass redefinition, CheckSummary methodSummary){
			if(fieldSummary.addedList.size() != 0){	
				System.err.println("\t Field(s) have been added.");
				System.err.println(fieldSummary.addedList);
				patchTransformer.transformFields(redefinition, fieldSummary.addedList, methodSummary.addedList);
			}else if(fieldSummary.removedList.size() != 0){
				System.err.println("\tField(s) has been removed");
				System.err.println(fieldSummary.removedList);
				for(Object generic : fieldSummary.removedList){
					//silly thing to have this flag
					SootField f = (SootField) generic;
					f.setDeclared(false);
					redefinition.addField(f);
                }
			}else if (!fieldSummary.changes){
				System.err.println("\tNo Field differences!");
			}
			
	}

	private static CheckSummary checkMethods(SootClass original, SootClass redefinition){
		HashMap<SootMethod, SootMethod> originalToRedefinitionMap = new HashMap<SootMethod, SootMethod>();
		CheckSummary methodSummary = new CheckSummary<SootMethod>();
		
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
																							
						
						if(sameType && sameName && sameParameters && !sameModifiers) {
							//modified modifiers
							matched = true;
							originalToRedefinitionMap.put(originalMethod, method);

							System.err.println("\t The following method has had modifiers altered: \n");
							System.err.println(originalMethod.getDeclaration() + "  --->  " + method.getDeclaration());
						}/* else if(sameType && sameModifiers && sameParameters){
							//modified name, actually NOT SURE
							matched = true;
							originalToRedefinitionMap.put(originalMethod, method);

							System.err.println("\t The following method has had its name altered: \n");
                            System.err.println(originalMethod.getDeclaration() + "  --->  " + method.getDeclaration());
							} */
						else if(sameModifiers && sameName && sameParameters && !sameType){
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
						methodSummary.addAddedItem(method);
					}
				}
			}
			for(SootMethod method : original.getMethods()){
				if(!originalToRedefinitionMap.containsKey(method) && !redefinitionHashMethods.containsKey(new Integer(ownEquivMethodHash(method)))) {
					methodSummary.addRemovedItem(method);
				}
			}

			if (originalToRedefinitionMap.size() == 0){
				methodSummary.changes = false;
			} else{
				methodSummary.changes = true;
			}
			
			return methodSummary;
	}
	private static void finishMethodSummary(CheckSummary methodSummary, SootClass redefinition){
			if(methodSummary.addedList.size() != 0){	
				System.err.println("\t Method(s) have been added.");
				System.err.println(methodSummary.addedList);
				//do the method stealing as we go
				patchTransformer.stealMethodCalls(redefinition, methodSummary.addedList);
			}else if(methodSummary.removedList.size() != 0){
				System.err.println("\tMethod(s) has been removed");
				System.err.println(methodSummary.removedList);
				for(Object generic : methodSummary.removedList){
					//silly thing to have this flag
					SootMethod m = (SootMethod)generic;
					m.setDeclared(false);
					redefinition.addMethod(m);
				}
			}else if (!methodSummary.changes){
				System.err.println("\tNo Method differences!");
			}
	}
	
	private static void checkInheritance(SootClass original, SootClass redefinition){
		boolean originalHasSuper = original.hasSuperclass();
		boolean redefinitionHasSuper = redefinition.hasSuperclass();
		if((originalHasSuper && !redefinitionHasSuper) || (!originalHasSuper  && redefinitionHasSuper)) {
			System.err.println("\tInheritance Diff!");
			System.err.println("\tOriginal class has superclass: " + original.getSuperclassUnsafe() + " and redefinition has superclass: " + redefinition.getSuperclassUnsafe());

		} else if(redefinition.hasSuperclass() && original.hasSuperclass() && !(redefinition.getSuperclass().getName().equals(original.getSuperclass().getName())) && !(redefinition.getSuperclass().getName()+originalRenameSuffix).equals(original.getSuperclass().getName())) {
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
	private static ArrayList<SootClass> gatherClassesFromDir(String strdir, String packagename){
		System.out.println("Gathering classes from : "+ strdir);
		ArrayList<String> allNames = new ArrayList<String>();
		try{
			File dir = new File(strdir);
			File[] directoryListing = dir.listFiles();
			if (directoryListing != null) {
				for (File file : directoryListing) {
					if(file.toString().contains("class")){
						//ugly parsing, its the only way tho?
						String classname = file.toString().replaceFirst(strdir , "").replace(".class", "").replaceAll("\\/", ".");
						System.out.println("Gathering class: "+ packagename+classname);
						allNames.add(packagename+classname);
						}
				}
			} else {
				System.out.println("Directory supplied is not sufficient to read.");
			}
		} catch(Exception e){
			System.out.println("Some issue accessing the classes to be renamed: "+ e.getMessage());
		}
		ArrayList<SootClass> allClasses = resolveClasses(allNames);
		return allClasses;
	}

	//reads the classes that designate the patch, from a file. One class per line, fqn.
	private static ArrayList<SootClass> gatherClassesFromFile(String configFile){
		ArrayList<SootClass> allClasses = new ArrayList<SootClass>();
		try{
			BufferedReader in = new BufferedReader(new FileReader(configFile));
			String str;
			ArrayList<String> allNames = new ArrayList<String>();
			while((str = in.readLine()) != null){
			allNames.add(str.replace(".class", "").replaceAll("\\/", "."));
			}
			allClasses = resolveClasses(allNames);
		}catch(Exception e){
            System.out.println("Some issue accessing the classes to be renamed: "+ e.getMessage());
        }
		return allClasses;
	}

	private static ArrayList<SootClass> resolveClasses(ArrayList<String> allNames){
		 ArrayList<SootClass> allClasses = new ArrayList<SootClass>();
		 for(String classname : allNames){
			 System.out.println("Resolving class: " + classname);
			 SootClass resolvedClass = Scene.v().forceResolve(classname, SootClass.BODIES);
			 allClasses.add(resolvedClass);
		 }
		 return allClasses;
	}

	public static List<String> getGeneratedClassesRedefs(){
		return allEmittedClassesRedefs;
	}
	public static List<String> getGeneratedClassesHosts(){
        return allEmittedClassesHostClasses;
    }
				
}
