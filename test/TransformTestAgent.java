import java.lang.instrument.ClassDefinition;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.lang.Thread;
import java.nio.file.Files;
import java.io.File;
import java.nio.file.Paths;

import differ.SemanticDiffer;

public class TransformTestAgent{

	private static String[] differArgs = {"-cp", "cpplaceholder", "-w", "-firstDest", "renamedOriginals", "-altDest", "adapterOutput", "-f", "c", "-redefcp", "diffdirplaceholder", "-runRename", "renameplaceholder",  "-mainClass", "TestRunner", "-originalclasslist", "originalclasslistplaceholder", "Example"}; 
	private static String[] argpieces;
	
	public static Instrumentation instrument;
    
    public static void premain(String args, Instrumentation inst){
	instrument = inst;
	System.out.println("Redefinition Agent started ...");

	Thread thread = new Thread("Weapon Thread") {
		public void run() {
		    try {
				System.out.println("Reading redefined class from: "+ args);
				argpieces = args.split("\\,");
				System.out.println("Using these arg pieces: "+ Arrays.toString(argpieces));
				System.err.println("Weapon thread sleeping for 1 sec ...");
				System.out.println("Weapon thread sleeping for 1 sec ...");
				Thread.sleep(1000);
				//gather all class files in the arg dir
				ArrayList<ClassDefinition> cdf = readDefsFromDir(argpieces[1]);
				System.out.println("Redefining Classes");
				System.err.println("Redefining Classes");
				instrument.redefineClasses(cdf.toArray(new ClassDefinition[cdf.size()]));
				System.err.println("Classes redefined ... weapon terminated");
				System.out.println("Classes redefined ... weapon terminated");
			
			} catch (Exception ex) {
				System.out.println("Cannot redefine with: "+ args);
				System.out.println("Encountered this exception: " + ex.getMessage());
				ex.printStackTrace();
				secondTry();
			}
			
		}

			public void secondTry(){
				try{
				//setup the args for invoking the difftool
				differArgs[1] = differArgs[1].replace("cpplaceholder", argpieces[2]);
				differArgs[10] = differArgs[10].replace("diffdirplaceholder", argpieces[1]);
				differArgs[12] = differArgs[12].replace("renameplaceholder", argpieces[3]);
				differArgs[16] = differArgs[16].replace("originalclasslistplaceholder", argpieces[0]);
				SemanticDiffer.main(differArgs);
				//will always be here
				String redefdir= Paths.get("").toAbsolutePath().toString() + "/adapterOutput/";
				ArrayList<ClassDefinition> cdf = readDefsFromDir(redefdir);
				System.out.println("Second Attempt at Redefining classes");
				System.err.println("Second Attempt Redefining classes");
				instrument.redefineClasses(cdf.toArray(new ClassDefinition[cdf.size()]));
				System.err.println("On Second Attempt classes redefined ... weapon terminated");
				System.out.println("On Second Attempt classes redefined ... weapon terminated");
				} catch (Exception ex) {
					System.out.println("Cannot redefine with adapterOutput classes.");
					ex.printStackTrace();
				}
			}

			private ArrayList<ClassDefinition> readDefsFromDir(String strdir){
				//gather all class files in the arg dir
				ArrayList<ClassDefinition> cdf = new ArrayList<ClassDefinition>();
				try {
					File dir = new File(strdir);
					File[] directoryListing = dir.listFiles();
					if (directoryListing != null) {
						for (File file : directoryListing) {
							if(file.toString().contains("class")){
								System.out.println("this was the absolute path: "+file.getAbsolutePath());
								String classname = file.getAbsolutePath().replaceFirst(strdir , "");
								System.out.println("Reading class: " + classname);
								byte[] classBytes= Files.readAllBytes(Paths.get(file.toString()));
								System.out.println("using classname: "+ classname.split("\\.")[0]);
								Class cls = Class.forName(classname.split("\\.")[0]);
								ClassDefinition element = new ClassDefinition( cls, classBytes );
								cdf.add(element);
							}
						}
					} else {
						System.err.println("Patch directory arg was insufficient: " + strdir);
						System.out.println("Patch directory arg was insufficient: " + strdir);
					}
					
				} catch (Exception e){
					System.out.println("Some issue with reading classfiles: "+ e.getMessage());
					e.printStackTrace();
				}
				return cdf;
        	}
			
		
	    };
	thread.start();
    }

}
