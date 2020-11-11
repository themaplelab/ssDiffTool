package testexamples;

public class NoChangeTest {

	public String hi;
	private int one;
	public static final String hello = "hello";
	protected static int two = 2;

	public static int calcSquare(int x){
		return x * x;
	}

	private void makeString(){
		one = 1;
		System.out.println(hello + two + one);
	}
	
}
