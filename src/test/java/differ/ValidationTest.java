import java.io.File;
import java.util.Arrays;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runners.MethodSorters;
import org.junit.FixMethodOrder;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ValidationTest {

	@Test
	public void ATestRunRename() throws Throwable {
	    String mainClass = "testexamples.nochange.NoChangeTest";
	    String[] classes = {mainClass};	    

	    TestSetup.makeListFiles(classes);	    
	    String[] differArgs =  TestSetup.setup(mainClass, "nochange", true);
	    TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);
	    
	    Class<?> clazz = TestSetup.validateClassFile(mainClass);
	    java.lang.reflect.Method methodHandle = clazz.getMethod("calcSquare", int.class);
	    Assert.assertEquals(16, methodHandle.invoke(null, 4));  
	    TestSetup.testSetupRefresh("testexamples/nochange");
	}

    @Test
    public void BTestRunFieldAddition() throws Throwable {
	String mainClass = "testexamples.fieldaddition.AdditionFieldTest";
	String[] classes = {mainClass};

	TestSetup.makeListFiles(classes);
	String[] differArgs =  TestSetup.setup(mainClass, "fieldaddition", true);
	TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);
	
	Class<?> clazz = TestSetup.validateClassFile(mainClass);

	Object obj = clazz.newInstance(); 
	
	java.lang.reflect.Method methodHandleOne = clazz.getMethod("returnSame", int.class);
	Assert.assertNotNull(methodHandleOne);
	Assert.assertEquals(3, methodHandleOne.invoke(obj, 3));

	java.lang.reflect.Method methodHandleTwo = clazz.getMethod("returnAValue");
	Assert.assertNotNull(methodHandleTwo);
        Assert.assertEquals(1, methodHandleTwo.invoke(obj));
	TestSetup.testSetupRefresh("testexamples/fieldaddition");
    }

    @Test
     public void CTestRunMethodAdditionMonomorphic() throws Throwable {
	String mainClass = "testexamples.methodadditionmonomorphic.AdditionMethodMonomorphicTest";
	String[] classes = {mainClass};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass, "methodadditionmonomorphic", true);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile(mainClass);

        Object obj = clazz.newInstance();

        java.lang.reflect.Method methodHandleOne = clazz.getMethod("returnSamePlusSeven", int.class);
        Assert.assertNotNull(methodHandleOne);
        Assert.assertEquals(8, methodHandleOne.invoke(obj, 1));
	TestSetup.testSetupRefresh("testexamples/methodadditionmonomorphic");
    }
    
    @Test
    public void DTestRunMethodAdditionPolymorphic() throws Throwable {
	String mainClass = "testexamples.methodadditionpolymorphic.UserPolyTest";
	String[] classes = {mainClass,
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOne",
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThree",
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSix"};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass, "methodadditionpolymorphic", true);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.UserPolyTest");

        Object obj = clazz.newInstance();
	
	java.lang.reflect.Method methodHandleOne = clazz.getMethod("use", int.class);
        Assert.assertNotNull(methodHandleOne);
        Assert.assertEquals(0, methodHandleOne.invoke(obj, 0));
	Assert.assertEquals(1, methodHandleOne.invoke(obj, 1));
	Assert.assertEquals(1, methodHandleOne.invoke(obj, 5));
	Assert.assertEquals(3, methodHandleOne.invoke(obj, 3));
        Assert.assertEquals(6, methodHandleOne.invoke(obj, 6));
	Assert.assertEquals(2, methodHandleOne.invoke(obj, 2));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 4));

	Class<?> childOne = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOneNewClass");
        java.lang.reflect.Method methodHandleChildOne = childOne.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOneNewClass", methodHandleChildOne.getDeclaringClass().getName());

	Class<?> childThree = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThreeNewClass");
        java.lang.reflect.Method methodHandleChildThree = childThree.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThreeNewClass", methodHandleChildThree.getDeclaringClass().getName());

	Class<?> childSix = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSixNewClass");
        java.lang.reflect.Method methodHandleChildSix = childSix.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSixNewClass", methodHandleChildSix.getDeclaringClass().getName());

	
	TestSetup.testSetupRefresh("testexamples/methodadditionpolymorphic");
    }

    @Test
    public void ETestRunMethodAdditionPolymorphic() throws Throwable {
	//tests the process dir approach of cg contruction
	String mainClass = "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOne";
        String[] classes = {mainClass,
			    "testexamples.methodadditionpolymorphic.UserPolyTest",
                            "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThree",
                            "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSix"};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass, "methodadditionpolymorphic", false);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.UserPolyTest");

        Object obj = clazz.newInstance();

        java.lang.reflect.Method methodHandleOne = clazz.getMethod("use", int.class);
        Assert.assertNotNull(methodHandleOne);
        Assert.assertEquals(0, methodHandleOne.invoke(obj, 0));
        Assert.assertEquals(1, methodHandleOne.invoke(obj, 1));
        Assert.assertEquals(1, methodHandleOne.invoke(obj, 5));
	Assert.assertEquals(3, methodHandleOne.invoke(obj, 3));
        Assert.assertEquals(6, methodHandleOne.invoke(obj, 6));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 2));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 4));

        Class<?> childOne = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOneNewClass");
        java.lang.reflect.Method methodHandleChildOne = childOne.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOneNewClass", methodHandleChildOne.getDeclaringClass().getName());

        Class<?> childThree = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThreeNewClass");
        java.lang.reflect.Method methodHandleChildThree = childThree.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThreeNewClass", methodHandleChildThree.getDeclaringClass().getName());
	
    Class<?> childSix = TestSetup.validateClassFile("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSixNewClass");
    java.lang.reflect.Method methodHandleChildSix = childSix.getMethod("emitMethod");
    Assert.assertEquals("testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSixNewClass", methodHandleChildSix.getDeclaringClass().getName());


    TestSetup.testSetupRefresh("testexamples/methodadditionpolymorphic");

    }

    @Test
    public void FTestRunMethodRemoval() throws Throwable {
        String mainClass = "testexamples.methodremoval.UserPolyTest";
        String[] classes = {mainClass,
                            "testexamples.methodremoval.AdditionMethodPolymorphicTestChildOne",
                            "testexamples.methodremoval.AdditionMethodPolymorphicTestChildThree",
                            "testexamples.methodremoval.AdditionMethodPolymorphicTestChildSix"};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass, "methodremoval", true);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile(mainClass);

        Object obj = clazz.newInstance();

        java.lang.reflect.Method methodHandleOne = clazz.getMethod("use", int.class);
        Assert.assertNotNull(methodHandleOne);
	Assert.assertEquals(0, methodHandleOne.invoke(obj, 0));
        Assert.assertEquals(0, methodHandleOne.invoke(obj, 1));
	Assert.assertEquals(0, methodHandleOne.invoke(obj, 5));
        Assert.assertEquals(0, methodHandleOne.invoke(obj, 3));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 6));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 2));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 4));

	Class<?> childOne = TestSetup.validateClassFile("testexamples.methodremoval.AdditionMethodPolymorphicTestChildOne");
	java.lang.reflect.Method methodHandleRMOne = childOne.getMethod("emitMethod");
	Assert.assertEquals("testexamples.methodremoval.AdditionMethodPolymorphicTestChildOne", methodHandleRMOne.getDeclaringClass().getName());

	 Class<?> childThree = TestSetup.validateClassFile("testexamples.methodremoval.AdditionMethodPolymorphicTestChildThree");
        java.lang.reflect.Method methodHandleRMThree = childThree.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodremoval.AdditionMethodPolymorphicTestChildThree", methodHandleRMThree.getDeclaringClass().getName());

	Class<?> childSix = TestSetup.validateClassFile("testexamples.methodremoval.AdditionMethodPolymorphicTestChildSix");
        java.lang.reflect.Method methodHandleRMSix = childSix.getMethod("emitMethod");
        Assert.assertEquals("testexamples.methodremoval.AdditionMethodPolymorphicTestChildSix", methodHandleRMSix.getDeclaringClass().getName());
	
	TestSetup.testSetupRefresh("testexamples/methodremoval");
    }
}

