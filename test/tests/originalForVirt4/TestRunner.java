import java.util.concurrent.TimeUnit;
import java.lang.Exception;

public class TestRunner{

    public static void main(String[] args){
		try {
		test(args);
		TimeUnit.SECONDS.sleep(65);
		test(args);
		}catch(Exception e){
        e.printStackTrace();
    }
	}

	public static void test(String[] args){
	try {
	    ChildHelloTwo hello;
		if(args[0].equals("parent")){
			hello = new ChildHelloTwo();
		}else{
			hello = new ChildHelloTwoChild();
		}
		System.out.println("I am the original test call");
		hello.addedMethod();
	}catch(Exception e){
	    e.printStackTrace();
	}
    }

}
