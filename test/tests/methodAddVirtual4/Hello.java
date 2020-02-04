//original Hello
public class Hello extends HelloTop{

    public static String HELLO = "Hello";
    public static String WORLD = "World";
    
    public Hello(){
    }

    public void printHello(){
	 System.out.println(HELLO);
    }

	public void addedMethod(){
        System.out.println("This is the method in Hello");
    }
}
