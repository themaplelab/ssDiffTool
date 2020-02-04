import java.util.concurrent.TimeUnit;
import java.lang.Exception;

public class TestRunner{

    public static void main(String[] args){
		try {
		test(args);
		TimeUnit.SECONDS.sleep(55);
		test(args);
		}catch(Exception e){
        e.printStackTrace();
    }
	}

	public static void test(String[] args){
	try {
	    HelloTop hello;
		if(args[0].equals("hellotop")){
			hello = new HelloTop();
		} else if(args[0].equals("hello")){
			hello = new Hello();
		} else if(args[0].equals("childhello")){
			hello = new ChildHello();
		} else if(args[0].equals("childhellotwo")){
			hello = new ChildHelloTwo();
		} else if(args[0].equals("childhellotop")){
            hello = new ChildHelloTop();
        } else if(args[0].equals("childhellotwochild")){
			hello = new ChildHelloTwoChild();
		} else{
			hello = new Hello();
		}
			
		System.out.println("I am the redefined test call");
		hello.addedMethod();
	}catch(Exception e){
	    e.printStackTrace();
	}
    }

}
