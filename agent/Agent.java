import java.lang.instrument.ClassDefinition;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataInputStream;
import java.net.ServerSocket;

import java.io.ObjectInputStream;

import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

public class Agent{

    public static Instrumentation instrument;
	
	private static int port = 49153;
	private static Socket clientSocket;
    private static ServerSocket serverSocket;
    private static PrintWriter out;
	private static ObjectInputStream in;
	
    public static void premain(String args, Instrumentation inst){
	instrument = inst;
	Thread thread = new Thread("Weapon Thread") {
		public void run() {
		    try {
				System.out.println("AGENT: Acquiring agent port!");
				serverSocket = new ServerSocket(port);
			System.out.println("-----------------------------------");
			System.out.println("AGENT: Starting up Agent server, waiting for client!");
			clientSocket = serverSocket.accept();
			out = new PrintWriter(clientSocket.getOutputStream(), true);

			in = new ObjectInputStream(clientSocket.getInputStream());
			
			System.out.println("-----------------------------------");
			System.out.println("AGENT: CogniCrypt client accepted.");
			String inputLine;
			while ((inputLine = in.readUTF()) != null) {
				if ("END".equals(inputLine)) {
					System.out.println("AGENT: Java Agent Terminating Connection.");
					stopServing();
					break;
				}

				if("INITREDEFINITION".equals(inputLine.trim())) {
					System.out.println("AGENT: preparing to do the redefinition...");
					int numberInPatch = in.readInt();
					if(numberInPatch>0) {
                        defineClasses(numberInPatch, in);
                    }
					numberInPatch = in.readInt();
					if(numberInPatch>0) {
						ArrayList<ClassDefinition> cdf = new ArrayList<ClassDefinition>();
						for(int i = 0; i < numberInPatch; i++){
							ClassDefinition element = readClassDefinition(in);
							if(element != null){
								cdf.add(element);
							}
						}
						instrument.redefineClasses(cdf.toArray(new ClassDefinition[cdf.size()]));
						System.out.println("AGENT: Class redefined ... weapon terminated.");
						stopServing();
						break;
					} else{
						System.out.println("AGENT: Expected to receive classname next, received nothing instead.");
					}
				}

		    }
			} catch (Exception ex) {
				System.out.println("AGENT: Cannot redefine.");
				ex.printStackTrace();
			}
		}

			private void defineClasses(int numberInPatch, ObjectInputStream in){
				try{
					Field f = Unsafe.class.getDeclaredField("theUnsafe");
					f.setAccessible(true);
					Unsafe unsafe = (Unsafe) f.get(null);
					byte[] classBytes = null;
					
					for(int i = 0; i < numberInPatch; i++){
						int length = in.readInt();
						classBytes = new byte[length];
						in.readFully(classBytes, 0, classBytes.length);
						String classname = in.readUTF();
						System.out.println("AGENT: Defining "+ classname);
						unsafe.defineClass(classname, classBytes, 0, classBytes.length, this.getClass().getClassLoader(), this.getClass().getProtectionDomain());
					}
				} catch(Exception e){
					System.out.println("Exception while defining classes: "+ e.getMessage());
					e.printStackTrace();
				}
				
			}
			
			private ClassDefinition readClassDefinition(ObjectInputStream in) throws Exception{
				ClassDefinition element = null;
				byte[] classBytes = null;
				try{
					int length = in.readInt();
					classBytes = new byte[length];
					in.readFully(classBytes, 0, classBytes.length); // read the message                                 
					Class redefclass = (Class) in.readObject();
					System.out.println("AGENT: Redefining "+ redefclass.getName());
					element = new ClassDefinition( redefclass, classBytes );
					
				} catch(ClassNotFoundException e){
					System.out.println("AGENT: class not found");
					e.printStackTrace();
					Field f = Unsafe.class.getDeclaredField("theUnsafe");
					f.setAccessible(true);
					Unsafe unsafe = (Unsafe) f.get(null);
					//todo fix this, send back non ack?
					unsafe.defineClass("", classBytes, 0, classBytes.length, this.getClass().getClassLoader(), this.getClass().getProtectionDomain());
					Class redefclass = (Class) in.readObject();
					element = new ClassDefinition( redefclass, classBytes );
					
				} catch (Exception ex) {
					System.out.println("AGENT: Cannot redefine.");
					ex.printStackTrace();
            }
				return element;
			}
		
			private void stopServing() throws Exception {
				System.out.println("AGENT: closing connection.");
				in.close();
				out.close();
				clientSocket.close();
				serverSocket.close();
			}
			
	    };
	thread.start();
    }

}
