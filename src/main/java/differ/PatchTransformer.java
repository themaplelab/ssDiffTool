package differ;

import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ExplicitEdgesPred;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import soot.jimple.ClassConstant;
import soot.Modifier;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Body;
import soot.Unit;
import soot.PatchingChain;
import soot.util.HashChain;
import soot.jimple.InvokeExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.DefinitionStmt;
import soot.Type;
import soot.VoidType;
import soot.Local;
import soot.ValueBox;
import soot.Value;
import soot.util.Chain;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.ThisRef;	

public class PatchTransformer{

	private HashMap<SootClass, SootClass> redefToNewClassMap;
	//we have guarantee 1:1 map, this is needed for ref fixing, is ugly but again, what else?
	private HashMap<SootClass, SootClass> newToRedefClassMap;
	//TODO rm newClassHasStaticInit in a cleanup commit
	private boolean newClassHasStaticInit = false;
	private Filter explicitInvokesFilter;
	//idk if this is best or second element on one hashmap should be list
	private HashMap<SootField, SootMethod> fieldToGetter = new HashMap<SootField, SootMethod>();
	private HashMap<SootField, SootMethod> fieldToSetter = new HashMap<SootField, SootMethod>();
	private HashMap<SootField, SootField> oldFieldToNew = new HashMap<SootField, SootField>();

	//TODO decide if this is still needed?
	private HashMap<SootMethodRef, SootMethodRef> oldMethodToNew = new HashMap<SootMethodRef, SootMethodRef>(); 
	private ArrayList<SootMethod> newMethods = new ArrayList<SootMethod>();
	
	public PatchTransformer(HashMap<SootClass, SootClass> newClassMap, HashMap<SootClass, SootClass> newClassMapReversed){
		//just want a ref, to same structure in SemanticDiffer
		this.redefToNewClassMap = newClassMap;
		this.newToRedefClassMap = newClassMapReversed;
		explicitInvokesFilter = new Filter(new ExplicitEdgesPred());
	}

	public void stealMethodCalls(SootClass redefinition, List<SootMethod> addedMethods){
		//remove the added methods from redefinition class
		//and place into wrapper class
		for(SootMethod m : addedMethods){
			SootMethodRef oldRef = m.makeRef();
			redefinition.removeMethod(m);
			redefToNewClassMap.get(redefinition).addMethod(m);
			oldMethodToNew.put(oldRef, m.makeRef());
			newMethods.add(m);
		}
		System.out.println("THISIS METHOD MAP");
		System.out.println(oldMethodToNew);
		System.out.println("These are the new methods list: "+ newMethods);
	}

	//checks all of the provided methods for method refs that need fixing
	public void transformMethodCalls(List<SootMethod> methods){
		//the annoying issue of adding a constructor to this class if needed, which is determined while looping the methods
		Chain<SootMethod> methodsChain = new HashChain<SootMethod>();
	    for(SootMethod m : methods){
			methodsChain.add(m);
		}
		for (Iterator<SootMethod> iter = methodsChain.snapshotIterator(); iter.hasNext();) {
			SootMethod m = iter.next();
			findMethodCalls(m);
        }
	}

	private void findMethodCalls(SootMethod m){
		CallGraph cg = Scene.v().getCallGraph();
		Body body;
		System.out.println("Finding method calls in : "+ m.getSignature());
		System.out.println("-------------------------------");
		body = m.retrieveActiveBody();

		//need to be able to concurrently mod
		PatchingChain<Unit> units = body.getUnits();
		Iterator<Unit> it = units.snapshotIterator();

		while (it.hasNext()) {

			Unit u = it.next();
			Stmt s = (Stmt)u;
			Unit insnAfterInvokeInsn = units.getSuccOf(u);
				
			if (s.containsInvokeExpr()) {
                InvokeExpr invokeExpr = s.getInvokeExpr();
				System.out.println("Found a method call: "+invokeExpr.getMethodRef() );
				System.out.println("Found in stmt: " + s);
				System.out.println("Printing the targets of this call: ");
				System.out.println(".....................................");
				Iterator targets = new Targets(explicitInvokesFilter.wrap(cg.edgesOutOf(s)));
				System.out.println(".....................................");
				if(targets.hasNext()){
					SootMethod target = (SootMethod) targets.next();
					System.out.println(target);
					//relying solely on targets in the cg, since method refs could have child classes that rely on parent defs
					if(newMethods.contains(target) && !targets.hasNext()){
						System.out.println("replacing a method call in this statement: "+ s);
						System.out.println(invokeExpr.getMethodRef() + " ---> " + target.makeRef());
						//condition where the cg has only one explicit target for this call
						if(invokeExpr instanceof StaticInvokeExpr){
							//if its a static invoke we can just replace ref
							invokeExpr.setMethodRef(target.makeRef());
						} else {
							//otherwise we gotta create a var of newClass type to invoke this on
							//TODO make this safe... need to singleton the locals ugh
							System.out.println("This is the redeftonewMap" + redefToNewClassMap);
							System.out.println("This is the target decl decl class: "+  target.getDeclaringClass().getName());
							SootClass newClass = target.getDeclaringClass();
							System.out.println("This is the newclass getType: "+  newClass.getType()); 
							Local invokeobj =  Jimple.v().newLocal("invokeobj", newClass.getType());
							body.getLocals().add(invokeobj);
							//TODO fix this for the methodrefs in the added methods, those should just use "this" not new local
							createInitializer(newClass);
							units.insertBefore(Jimple.v().newAssignStmt(invokeobj, Jimple.v().newNewExpr(newClass.getType())), u);
							units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(invokeobj, newClass.getMethodUnsafe("<init>", Arrays.asList(new Type[]{}), VoidType.v()).makeRef())) , u);
							units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(invokeobj, target.makeRef(), invokeExpr.getArgs())) , u);
							units.remove(u);
						}
					} else {
						if(targets.hasNext()){
							System.out.println("There are potentially multiple targets in this stmt: " + s);
							System.out.println("................................");
							//todo refactor maybe
							if(newMethods.contains(target)){
								//this target was one that we moved from a redefinition into a newClass                     
								System.out.println("This target is one we stole: "+ target);
								//TODOBUILDGUARD
								//if ref.getClass == method.getDeclaringClass:
								//    method call
								constructGuard(target, body , units, insnAfterInvokeInsn, u);
							}
							while(targets.hasNext()){
								target = (SootMethod) targets.next();
								 if(newMethods.contains(target)){
									 System.out.println("This target is one we stole: "+ target);
									 constructGuard(target, body , units, insnAfterInvokeInsn, u);
								 }
								 else {
									System.out.println(target);
								 }
							}
							System.out.println("................................");
						}
					}
					
				}else{
					System.out.println("Did not do anything with this statment: " + s + " because there were no targets at all.");
				}
			}
		System.out.println("-------------------------------");
		}
	}

	/*
	 * constructs a check on the runtime
	 * type of the object that a method is 
	 * invoked upon. if it matches type
	 * that we stole from, then execution 
	 * goes to a new block in program
	 * that calls the new method in its new class
	 *
	 * original program:
	 * ` a.someMethod()`
	 *
	 * fixed program:
	 * ```
	 * if(a.getClass == robbedClass.class):
     *    robberClass.someMethod()
     * else if:
     *    //possibly more checks
     * else:
	 *    a.someMethod() // this may not be here if a no longer holds someMethod
	 *
	 * ```
	 *
	 */
	private void constructGuard(SootMethod target, Body body, PatchingChain<Unit> units, Unit insnAfterInvokeInsn, Unit currentInsn){
		InvokeExpr invokeExpr = ((Stmt)currentInsn).getInvokeExpr();
		System.out.println("This target is one we stole: "+ target);
		//already performed the steal by now, so the current decl class is correct          
		SootClass newClass = target.getDeclaringClass();

		//create the new block that contains the call to the new method
		System.out.println("This is the newclass getType: "+  newClass.getType());
		Local invokeobj =  Jimple.v().newLocal("invokeobj", newClass.getType());
		body.getLocals().add(invokeobj);
		//TODO fix this for the methodrefs in the added methods, those should just use "this" not new local
		
		createInitializer(newClass);
		Unit newBlock = Jimple.v().newAssignStmt(invokeobj, Jimple.v().newNewExpr(newClass.getType()));
		units.addLast(newBlock);
		units.addLast(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(invokeobj, newClass.getMethodUnsafe("<init>", Arrays.asList(new Type[]{}), VoidType.v()).makeRef())));
		System.out.println("This is the method ref: "+ invokeExpr.getMethodRef());
		units.addLast(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(invokeobj, target.makeRef(), invokeExpr.getArgs())));
		//then jump back to insn after u
		units.addLast(Jimple.v().newGotoStmt(insnAfterInvokeInsn));

		//construct the check for the runtime type
		SootMethod getClass = Scene.v().getMethod("<java.lang.Object: java.lang.Class getClass()>");
		
		//construct locals to set as the types we want to compare                           
		//the benefit/drawback of 3 address, do we actually need???                         
		Local clz = Jimple.v().newLocal("clz", Scene.v().getType("java.lang.Class"));
		body.getLocals().add(clz);
		System.out.println("These are now the locals: "+ body.getLocals());
		
		units.insertBefore(Jimple.v().newAssignStmt(clz, Jimple.v().newVirtualInvokeExpr((Local)((InstanceInvokeExpr)invokeExpr).getBase(), getClass.makeRef())), currentInsn);
		
		System.out.println("This is the (newToRedefClassMap: "+ newToRedefClassMap);
		units.insertBefore(Jimple.v().newIfStmt(Jimple.v().newEqExpr(clz, ClassConstant.fromType(newToRedefClassMap.get(target.getDeclaringClass()).getType())), newBlock), currentInsn);
	}
	
	public void transformFields(SootClass redefinition, List<SootField> addedFields){
		//fixFieldRefs(redefinition);
		for(SootField field : addedFields){
			fixFields(field, redefinition);
		}

		fixFieldRefs(redefinition);

		for(SootField field : addedFields){
			redefinition.removeField(field);
		}
	}

	//adds the field to the new class and if the field was private constructs an accessor for it
	private void fixFields(SootField field, SootClass redefinition){
		//cant actually just move the same field ref, need it to exist in both classes simultaneously in order to fix refs
		SootField newField = new SootField(field.getName(), field.getType(), field.getModifiers());
		SootClass newClass = redefToNewClassMap.get(redefinition);
		newClass.addField(newField); 
		oldFieldToNew.put(field, newField);
		
		System.out.println("is the newfield static?: "+ newField.isStatic());
		System.out.println("is the newfield phantom for some reason?:"+ newField.isPhantom());

		//construct getter and setter for even public fields
		String methodName =	"get"+ field.getName();

		//getter first
			//need the acessor to be public , might need to also be static
			int modifiers = Modifier.PROTECTED;
			if(field.isStatic()){
				modifiers |= Modifier.STATIC;
			}
			SootMethod newGetter = new SootMethod(methodName, Arrays.asList(new Type[]{}), field.getType(), modifiers);

			JimpleBody getterBody = Jimple.v().newBody(newGetter);
			newGetter.setActiveBody(getterBody);
			Chain units = getterBody.getUnits();

			//must create local then can return that
			Local tmpref =  Jimple.v().newLocal("tmpref", field.getType());
			getterBody.getLocals().add(tmpref);
			units.add(Jimple.v().newAssignStmt(tmpref, Jimple.v().newStaticFieldRef(newField.makeRef())));
			units.add(Jimple.v().newReturnStmt(tmpref));
			
			newClass.addMethod(newGetter);
			fieldToGetter.put(field, newGetter);

			//make setter too
			String setterMethodName = "set"+ field.getName();
			//may have some issue here if there was casting for stmts we will replace...
			SootMethod newSetter = new SootMethod(setterMethodName, Arrays.asList(new Type[]{field.getType()}), VoidType.v(), modifiers);

            JimpleBody setterBody = Jimple.v().newBody(newSetter);
			newSetter.setActiveBody(setterBody);
			Chain setterUnits = setterBody.getUnits();
			//have to assign the param to a local first, cannot go directly from param to assignstmt, idk exactly why
			Local paramref =  Jimple.v().newLocal("paramref", field.getType());
			setterBody.getLocals().add(paramref);
			setterUnits.add(Jimple.v().newIdentityStmt(paramref, Jimple.v().newParameterRef(field.getType(), 0)));
			setterUnits.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(newField.makeRef()), paramref));
			setterUnits.add(Jimple.v().newReturnVoidStmt());
			newClass.addMethod(newSetter);
			fieldToSetter.put(field, newSetter);
        }


	private void fixFieldRefs(SootClass redefinition){
		for(SootMethod m : redefinition.getMethods()){
			System.out.println("Finding  field refs in : "+ m.getSignature());
			System.out.println("name is: "+ m.getName());
			System.out.println("-------------------------------");
			Body body = m.retrieveActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			Iterator<Unit> it = units.snapshotIterator();

			while (it.hasNext()) {

				Unit u = it.next();
				Stmt s = (Stmt)u;
				if(s.containsFieldRef()){
					SootField ref = s.getFieldRef().getField();
					SootField newref = oldFieldToNew.get(ref);

					if(containsUse(u, ref)){
						System.out.println("-------------------------------");
						System.out.println("found a use of: "+ ref);
						System.out.println("in stmt: "+ s);
						System.out.println("-------------------------------");
					}else{
						System.out.println("-------------------------------");
						System.out.println("must have been a def of: "+ ref);
						System.out.println("-------------------------------");
					}
					
					if(newref != null){

						//must steal initialization if the field is static
						// is the second check even needed?
						if(m.getName().equals("<clinit>") && ref.isStatic()){
							units.remove(u);
							SootClass newClass = redefToNewClassMap.get(redefinition);
							createStaticInitializer(newClass);
							System.out.println("This is wahts in the box atm: " + s.getFieldRefBox().getValue());
							s.getFieldRefBox().setValue(Jimple.v().newStaticFieldRef(oldFieldToNew.get(ref).makeRef()));
							newClass.getMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()).retrieveActiveBody().getUnits().addFirst(u);

						}else {

							ValueBox fieldref = s.getFieldRefBox();
							if(u.getUseBoxes().contains(fieldref)){

								SootMethod newAccessor = fieldToGetter.get(ref);
								System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
								System.out.println("in this statement: "+ s);
								
								Local tmpRef = Jimple.v().newLocal("tmpRef", ref.getType());
								body.getLocals().add(tmpRef);
								
								units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticInvokeExpr(newAccessor.makeRef())), u);

								s.getFieldRefBox().setValue(tmpRef);
								
							}
							if(u.getDefBoxes().contains(fieldref)){

								SootMethod newAccessor = fieldToSetter.get(ref);
								System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
								System.out.println("in this statement: "+ s);
								//def boxes only to be nonempty on identitystmts or assignstmts
								units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
								units.remove(u);
								

							}
						}
					}

				}
			}
		}
	}

	private void createInitializer(SootClass newClass){
		//only want to create one initialization function
		 if(newClass.getMethodUnsafe("<init>", Arrays.asList(new Type[]{}), VoidType.v()) == null){
			 //is that a good access level? no reason to not use public, but ... should we?
			 SootMethod initializer = new SootMethod("<init>", Arrays.asList(new Type[]{}), VoidType.v(), Modifier.PROTECTED);
			 JimpleBody body = Jimple.v().newBody(initializer);
			 initializer.setActiveBody(body);
			 Chain units = body.getUnits();
			 Local selfref =  Jimple.v().newLocal("selfref", newClass.getType());
			 body.getLocals().add(selfref);
			 units.add(Jimple.v().newIdentityStmt(selfref, new ThisRef(newClass.getType())));
			 SootMethod parentConstructor = newClass.getSuperclass().getMethodUnsafe("<init>", Arrays.asList(new Type[]{}), VoidType.v());
			 System.out.println("This is the newclass's parent: "+ newClass.getSuperclass());
			 if(parentConstructor == null){
				 System.out.println("The parent constructor was null...");
				 //this cannot happen... the newClass is not Object... should throw here maybe
			 }
			 units.add(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(selfref, parentConstructor.makeRef())));
			 units.add(Jimple.v().newReturnVoidStmt());
			 newClass.addMethod(initializer);
		 }
	}
		
	private void createStaticInitializer(SootClass newClass){
		//only want to create one static initialization function
		if(newClass.getMethodUnsafe("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()) == null){
			SootMethod initializer = new SootMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v(), Modifier.STATIC);
			JimpleBody body = Jimple.v().newBody(initializer);
            initializer.setActiveBody(body);
			Chain units = body.getUnits();
			units.add(Jimple.v().newReturnVoidStmt());
            newClass.addMethod(initializer);
			newClassHasStaticInit = true;
		}
	}

	private boolean containsUse(Unit u, SootField ref){
		System.out.println("starting use check");
		for(ValueBox value : u.getUseBoxes()){
			System.out.println(value.getValue());
			System.out.println(value.getValue().equivHashCode());
			System.out.println(ref.equivHashCode());
			if(value.getValue().equivHashCode() == ref.equivHashCode()){
				return true;
			}
		}
		return false;
	}

}
