package differ;

import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ExplicitEdgesPred;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import soot.jimple.ClassConstant; //todo check if used
import soot.jimple.IntConstant;
import soot.jimple.StringConstant;
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
import soot.jimple.InstanceFieldRef;
import soot.jimple.Stmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.NopStmt;
import soot.Type;
import soot.BooleanType;
import soot.IntType;
import soot.VoidType;
import soot.Local;
import soot.ValueBox;
import soot.Value;
import soot.RefType;
import soot.util.Chain;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.ThisRef;	

public class PatchTransformer{

	private HashMap<SootClass, SootClass> redefToNewClassMap;
	//we have guarantee 1:1 map, this is needed for ref fixing, is ugly but again, what else?
	private HashMap<SootClass, SootClass> newToRedefClassMap;
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

	public void stealMethodCalls(SootClass redefinition, List<SootMethod> addedMethods ){
		//remove the added methods from redefinition class
		//and place into wrapper class
		for(SootMethod m : addedMethods){
			SootMethodRef oldRef = m.makeRef();
			redefinition.removeMethod(m);
			 if(m.isPrivate()){
				 //clear private, set protected                                                                           
				 m.setModifiers((m.getModifiers()&(~Modifier.PRIVATE)) | Modifier.PROTECTED);
			 }
			redefToNewClassMap.get(redefinition).addMethod(m);
			oldMethodToNew.put(oldRef, m.makeRef());
			newMethods.add(m);
		}
		System.out.println("THISIS METHOD MAP");
		System.out.println(oldMethodToNew);
		System.out.println("These are the new methods list: "+ newMethods);
	}

	//checks all of the provided methods for method refs that need fixing
	public void transformMethodCalls(List<SootMethod> methods, boolean isThisARedefClass ){
		//the annoying issue of adding a constructor to this class if needed, which is determined while looping the methods
		Chain<SootMethod> methodsChain = new HashChain<SootMethod>();
	    for(SootMethod m : methods){
			methodsChain.add(m);
		}
		for (Iterator<SootMethod> iter = methodsChain.snapshotIterator(); iter.hasNext();) {
			SootMethod m = iter.next();
			findMethodCalls(m, isThisARedefClass);
        }
	}

	private void findMethodCalls(SootMethod m, boolean isThisARedefClass ){
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
					Iterator newTargets = new Targets(explicitInvokesFilter.wrap(cg.edgesOutOf(s)));
					ArrayList<SootMethod> sortedTargets = sortTargets(newTargets);
					boolean checkForMapped = checkForMapped(sortedTargets);

					if(checkForMapped){
						//relying solely on targets in the cg, since method refs could have child classes that rely on parent defs
						if(sortedTargets.size() == 1){
							SootMethod target = sortedTargets.get(0);
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
								constructNewCall(newClass, invokeExpr, units, u, body, isThisARedefClass);
							}
						} else {
						//more than one target
							for(SootMethod target : sortedTargets){
								System.out.println("This target is one we are fixing: "+ target);
								constructGuard(target, body , units, insnAfterInvokeInsn, u);
							}
							units.remove(u);
								//now possibly replace the static method ref here as well
								//if(oldToNewMethod.get(invokeExpr.getMethodRef()) != null){
								//	Jimple.v().newInstanceOfExpr(invokeExpr).getBase(), invokeExpr.getMethod().getDeclaringClass());
							//}
							System.out.println("................................");
						}
			
					}else{
                    System.out.println("Did not do anything with this statment: " + s + " because there were no relevant targets.");
                }
				}else{
					System.out.println("Did not do anything with this statment: " + s + " because there were no targets at all.");
				}
			}
		System.out.println("-------------------------------");
		}
	}

	//determines if any of the targets correspond to stolen methods, only care about changing calls that may have stolen targets
	private boolean checkForMapped(ArrayList<SootMethod> sorted){
		for(SootMethod target : sorted){
			if(newMethods.contains(target)){
				return true;
			}
		}
		return false;
	}
	
	//this is a dumb way to sort things, but since the targets are implemented as iterator I think it needs to be this way
	//currently ONLY considers one lineage of hierarchy... not realistic if we begin to consider reflection later
	private ArrayList<SootMethod> sortTargets(Iterator newTargets){
		//map of parents to possibly multiple children
		HashMap<SootClass, LinkedHashSet<SootClass>> unsorted =  new HashMap<SootClass, LinkedHashSet<SootClass>>();
		HashMap<SootClass, SootMethod> unsortedDeclToMethods = new HashMap<SootClass, SootMethod>();
		//targets sorted in order of children to parents
		ArrayList<SootClass> sortedHierarchy = new ArrayList<SootClass>();
		ArrayList<SootMethod> sortedHierarchyMethods = new ArrayList<SootMethod>();
		SootClass object = Scene.v().getSootClass("java.lang.Object");
		
		while(newTargets.hasNext()){
			SootMethod target = (SootMethod) newTargets.next();
			System.out.println("This is a target out of the iterator: "+ target);
			if(target.getDeclaringClass()!= object){
				if(!unsorted.containsKey(target.getDeclaringClass().getSuperclass())){
					unsorted.put(target.getDeclaringClass().getSuperclass(), new LinkedHashSet<SootClass>());
				}
				SootClass toAdd = target.getDeclaringClass();
				if(newToRedefClassMap.get(toAdd)!= null){
					toAdd = newToRedefClassMap.get(toAdd);
				}
				unsorted.get(target.getDeclaringClass().getSuperclass()).add(toAdd);
				unsortedDeclToMethods.put(toAdd, target);
			}
		}

		//start with known top of hierarchy
		SootClass index = object;
		traverseChildren(sortedHierarchy, unsorted, index);

		for(SootClass sootclass : sortedHierarchy){
			sortedHierarchyMethods.add(unsortedDeclToMethods.get(sootclass));
		}
		
		System.out.println("this is the unsorted list: "+ unsorted);
		System.out.println("this is the sorted hierarchy"+ sortedHierarchy);
		System.out.println("this is the sorted hierarchy of methods"+ sortedHierarchyMethods);
		return sortedHierarchyMethods;
	}

	private void traverseChildren(ArrayList<SootClass> sortedHierarchy, HashMap<SootClass, LinkedHashSet<SootClass>> unsorted, SootClass index){
		if(unsorted.get(index) != null){
			for(SootClass child : unsorted.get(index)){
                sortedHierarchy.add(0, child);
            }
			for(SootClass child : unsorted.get(index)){
				traverseChildren(sortedHierarchy, unsorted, child);
			}
		}
	}

	private void constructNewCall(SootClass newClass, InvokeExpr invokeExpr, PatchingChain<Unit> units, Unit currentInsn, Body body, boolean isThisARedefClass){

		System.out.println("This is the newclass getType: "+  newClass.getType());
		Local newClassRef;

		if(isThisARedefClass){
			Value base = ((InstanceInvokeExpr)invokeExpr).getBase();
			newClassRef = lookup(newClass, body, units, currentInsn, base);
		}else{
			newClassRef = body.getThisLocal();
		}

		SootMethodRef newClassMethod = newClass.getMethod(invokeExpr.getMethodRef().getSubSignature()).makeRef();
		System.out.println("replacing a method call in this statement: "+ (Stmt)currentInsn);
		System.out.println(invokeExpr.getMethodRef() + " ---> " + newClassMethod);
		units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(newClassRef, newClassMethod, invokeExpr.getArgs())) , currentInsn);
		units.remove(currentInsn);
	}

	
	/*
	 * constructs a check on the runtime
	 * type of the object that a method is 
	 * invoked upon. if it matches type
	 * that we stole from, then execution 
	 * goes to a new block in program
	 * that calls the new method in its new class
	 * The checks are instanceof AND they are
	 * ordered from CHILD -> PARENT
	 *
	 * original program:
	 * ` a.someMethod()`
	 *
	 * fixed program:
	 * ```
	 * hostClass = mapRedefToNew[targetClass] || targetClass
	 * if(baseVar instanceof hostClass):
     *    hostClass.someMethod()
     * else if:
     *    //possibly more checks
	 *
	 * ```
	 *
	 */
	private void constructGuard(SootMethod target, Body body, PatchingChain<Unit> units, Unit insnAfterInvokeInsn, Unit currentInsn){
		InvokeExpr invokeExpr = ((Stmt)currentInsn).getInvokeExpr();
		//already performed the steal by now, so the current decl class is correct          
		SootClass newClass = target.getDeclaringClass();
		//may or may not actually be a newclass, if it is, need to instanitate and also know this for the instanceof check
		SootClass originalClass = newToRedefClassMap.get(newClass);
		Local invokeobj;
		Unit nextUnit;
		Unit newBlock;
		Type checkType;

		//create the check
		Local base = (Local)((InstanceInvokeExpr)invokeExpr).getBase();
        Local boolInstanceOf = Jimple.v().newLocal("boolInstanceOf", BooleanType.v());
        body.getLocals().add(boolInstanceOf);

		NopStmt nop = Jimple.v().newNopStmt();
		checkType = newClass.getType();
		if(originalClass != null){
			checkType =originalClass.getType();
		}
		units.insertBefore(Jimple.v().newAssignStmt(boolInstanceOf, Jimple.v().newInstanceOfExpr(base , checkType)) , currentInsn);
		units.insertBefore(Jimple.v().newIfStmt(Jimple.v().newEqExpr(boolInstanceOf, IntConstant.v(0)), nop), currentInsn);



		
		//create the new block that contains the call to the new method
		System.out.println("This is the newclass getType: "+  newClass.getType());
		
		//maybe TODO: fix this for the methodrefs in the added methods, those should just use "this" not new local

		//TEMP printcall so we can understand this bla
		Local tmpRef = Jimple.v().newLocal("tmpRef", RefType.v("java.io.PrintStream"));
		body.getLocals().add(tmpRef);
		units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticFieldRef(Scene.v().getField("<java.lang.System: java.io.PrintStream out>").makeRef())), currentInsn);
		SootMethod toCall = Scene.v().getMethod("<java.io.PrintStream: void println(java.lang.String)>");
		units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(tmpRef, toCall.makeRef(), StringConstant.v("Detected an instanceof condition!"))), currentInsn);
		
		if(originalClass == null){

			//this is where we did not steal
			invokeobj =  Jimple.v().newLocal("baseinvokeobj", newClass.getType());
			body.getLocals().add(invokeobj);
			units.insertBefore(Jimple.v().newAssignStmt(invokeobj, Jimple.v().newCastExpr(base, newClass.getType())), currentInsn);
			units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(invokeobj, target.makeRef(), invokeExpr.getArgs())), currentInsn);
			

			

		}else{

			invokeobj = lookup(newClass, body, units, currentInsn, base);
			//build the new call 
			units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(invokeobj, target.makeRef(), invokeExpr.getArgs())), currentInsn);

		}

		
		units.insertBefore(Jimple.v().newGotoStmt(insnAfterInvokeInsn), currentInsn);
		
		System.out.println("This is the method ref: "+ invokeExpr.getMethodRef());
		System.out.println("This is the method ref declaring class: "+ invokeExpr.getMethodRef().getDeclaringClass());

		//not sure if needed, I think dead code
		units.insertBefore(nop, currentInsn);
		
		System.out.println("This is the (newToRedefClassMap: "+ newToRedefClassMap);
	}
	
	public void transformFields(SootClass redefinition, List<SootField> addedFields, List<SootMethod> addedMethods){
		//fixFieldRefs(redefinition);
		for(SootField field : addedFields){
			fixFields(field, redefinition);
		}

		fixFieldRefs(redefinition, addedMethods);

		for(SootField field : addedFields){
			System.out.println("Removing this field: "+field.getSignature()+" from this class: "+redefinition.getName());
			redefinition.removeField(field);
		}
	}

	//adds the field to the new class and if the field was private constructs an accessor for it
	private void fixFields(SootField field, SootClass redefinition){
		//cant actually just move the same field ref, need it to exist in both classes simultaneously in order to fix refs
		SootField newField = new SootField(field.getName(), field.getType(), field.getModifiers());

		if(newField.isPrivate()){
			//clear private, set protected, so we can do bad direct access
			//TODO fix the later accesses to all be thru the accessors
			newField.setModifiers((newField.getModifiers()&(~Modifier.PRIVATE)) | Modifier.PROTECTED);
		}

		SootClass newClass = redefToNewClassMap.get(redefinition);
		newClass.addField(newField);
		System.out.println("Adding this field: "+ newField.getSignature()+ " to this new class: "+ newClass.getName());
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
			if(field.isStatic()){
		   	units.add(Jimple.v().newAssignStmt(tmpref, Jimple.v().newStaticFieldRef(newField.makeRef())));

			
			}else{
				//assign a local for self so we can ref our own field
				Local selfref =  Jimple.v().newLocal("selfref", newClass.getType());
				getterBody.getLocals().add(selfref);
				units.add(Jimple.v().newIdentityStmt(selfref, new ThisRef(newClass.getType())));
				units.add(Jimple.v().newAssignStmt(tmpref, Jimple.v().newInstanceFieldRef(selfref, newField.makeRef())));
			}

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
			if(field.isStatic()){
 				setterUnits.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(newField.makeRef()), paramref));
			}else{
				//assign a local for self so we can ref our own field				
				
                Local selfref =  Jimple.v().newLocal("selfref", newClass.getType());
                setterBody.getLocals().add(selfref);
				setterUnits.add(Jimple.v().newAssignStmt(Jimple.v().newInstanceFieldRef(selfref , newField.makeRef()), paramref));
				setterUnits.addFirst(Jimple.v().newIdentityStmt(selfref, new ThisRef(newClass.getType())));
			}
	
			setterUnits.add(Jimple.v().newReturnVoidStmt());
			newClass.addMethod(newSetter);
			fieldToSetter.put(field, newSetter);
        }


	private void fixFieldRefs(SootClass redefinition, List<SootMethod> addedMethods){
		for(SootMethod m : redefinition.getMethods()){
			boolean addedMethod = false;
			if(addedMethods.contains(m)){
				addedMethod = true;
				System.out.println("Finding  field refs in AN ADDED METHOD: "+ m.getSignature());
			}else{
				System.out.println("Finding  field refs in : "+ m.getSignature());
			}
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
						System.out.println("in stmt: "+ s);
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
								Local tmpRef;
								 if(addedMethod){
									 
									 tmpRef = Jimple.v().newLocal("tmpRef", ref.getType());
									 body.getLocals().add(tmpRef);
									 //the field ref is in a stolen method
									 if(ref.isStatic()){
										 System.out.println("building a static getter ref in added method");
										 units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticInvokeExpr(newAccessor.makeRef())), u);
									 }else{
										 System.out.println("building an instance getter ref in added method");
										 units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newVirtualInvokeExpr(body.getThisLocal(), newAccessor.makeRef())), u);
									 }

									 
								 }else{
									 SootClass newClass = redefToNewClassMap.get(redefinition);
									 tmpRef = Jimple.v().newLocal("tmpRef", newClass.getType());
                                     body.getLocals().add(tmpRef);
									 
									 //the field ref is in the redefinition class still, or some other class  
									 if(ref.isStatic()){
										 System.out.println("building a static direct field (USE) access in non added method");
units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticFieldRef(newClass.getFieldByName(ref.getName()).makeRef())), u);
										 
                                     }else{
										 System.out.println("building a an instance field (USE) access in a non added method");

										 Local newClassRef = lookup(newClass, body, units, u, ((InstanceFieldRef)s.getFieldRef()).getBase());
										 
										 units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newInstanceFieldRef(newClassRef, newref.makeRef())), u); //a bit of a redundant stmt but here for now

									 }

									  s.getFieldRefBox().setValue(tmpRef);
									 
								 }
								 
							}




							if(u.getDefBoxes().contains(fieldref)){

								SootMethod newAccessor = fieldToSetter.get(ref);
								System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
								System.out.println("in this statement: "+ s);
								if(addedMethod){

								//def boxes only to be nonempty on identitystmts or assignstmts
									if(ref.isStatic()){
										System.out.println("building a static setter ref in added method");
										units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
									}else{
										System.out.println("building an instance setter ref in added method");
										units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr((Local)((InstanceFieldRef)s.getFieldRef()).getBase(), newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
									}
								}else{

									 //the field ref is in the redefinition class still, or some other class       
                                     if(ref.isStatic()){
										 System.out.println("building an static field setter access in a non added meethod");
                                         units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
										 
                                     }else{
										 System.out.println("building an instance field (DEF) access in a non added meethod");
                                         SootClass newClass = redefToNewClassMap.get(redefinition);
                                         Local newClassRef = Jimple.v().newLocal("newClassRef", newClass.getType());
                                         body.getLocals().add(newClassRef);
                                         Local objecttemp = Jimple.v().newLocal("objecttemp", RefType.v("java.lang.Object"));
                                         body.getLocals().add(objecttemp);
                                         Local HT = Jimple.v().newLocal("HTFieldTemp", RefType.v("java.util.Hashtable"));
                                         body.getLocals().add(HT);

                                         units.insertBefore(Jimple.v().newAssignStmt(HT, Jimple.v().newStaticFieldRef(newClass.getFieldByName("originalToHostHashTable").makeRef())), u);

										 SootMethodRef mapGetter = Scene.v().getMethod("<java.util.Hashtable: java.lang.Object get(java.lang.Object)>").makeRef();
 
                                         units.insertBefore(Jimple.v().newAssignStmt(objecttemp, Jimple.v().newVirtualInvokeExpr(HT, mapGetter, Arrays.asList( new Value[]{((InstanceFieldRef)s.getFieldRef()).getBase()}))), u);
                                         units.insertBefore(Jimple.v().newAssignStmt(newClassRef, Jimple.v().newCastExpr(objecttemp, newClass.getType())), u);
                                         
										 units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(newClassRef, newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
									 }
								}	 
								units.remove(u);
								
							}
						}
						
					}
				}
			}
		}
	}

	public static void createInitializer(SootClass newClass){
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
		
	private static void createStaticInitializer(SootClass newClass){
		//only want to create one static initialization function
		if(newClass.getMethodUnsafe("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()) == null){
			SootMethod initializer = new SootMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v(), Modifier.STATIC);
			JimpleBody body = Jimple.v().newBody(initializer);
            initializer.setActiveBody(body);
			Chain units = body.getUnits();
			units.add(Jimple.v().newReturnVoidStmt());
            newClass.addMethod(initializer);
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

       /*
        * builds two maps
        * hostToOriginal object refs
        * originalToHost object refs
        * not efficient, but we need both ways
        */
       public static void buildHostMaps(SootClass newClass, String fieldname){

               createStaticInitializer(newClass);

               /* same beginning:                                                                                      
         * java.util.Hashtable $r0;                                                                               
         * $r0 = new java.util.Hashtable;                                                                         
         * specialinvoke $r0.<java.util.Hashtable: void <init>()>();                                              
         * <Hashtableexample: java.util.Hashtable xfieldtable> = $r0;                                             
                */
               //add the field                                                                                            
        RefType HT = RefType.v("java.util.Hashtable");
        SootField replacementField = new SootField(fieldname +"HashTable", HT, Modifier.PUBLIC | Modifier.STATIC);
        newClass.addField(replacementField);
               //initialize it bc its static                                                                             
               Body clinitBody = newClass.getMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()).retrieveActiveBody();
        PatchingChain<Unit> clinitUnits = clinitBody.getUnits();

        Local hashtable =  Jimple.v().newLocal("HTTemp", HT);
        clinitBody.getLocals().add(hashtable);

        //build in bottom up order, bc we dont have any other reference point in the method atm
               
        clinitUnits.addFirst(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(replacementField.makeRef()), hashtable));
        SootMethod toCall = Scene.v().getMethod("<java.util.Hashtable: void <init>()>");
        clinitUnits.addFirst(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(hashtable, toCall.makeRef())));
        clinitUnits.addFirst(Jimple.v().newAssignStmt(hashtable, Jimple.v().newNewExpr(HT)));
       }

       public static void setupRedefInit(SootClass newClass, SootClass redef){
               Body body = redef.getMethodByName("<init>").retrieveActiveBody();
			   PatchingChain<Unit> units = body.getUnits();;
               RefType HT = RefType.v("java.util.Hashtable");
               Local hashtableOGToHost =  Jimple.v().newLocal("hashtableOGToHost", HT);
               body.getLocals().add(hashtableOGToHost);
               Local hashtableHostToOG =  Jimple.v().newLocal("hashtableHostToOG", HT);
               body.getLocals().add(hashtableHostToOG);
               Local newclassref = Jimple.v().newLocal("newclassref", redef.getType());
               body.getLocals().add(newclassref);

               Local thisref = body.getThisLocal();
               Unit thisUnit = body.getThisUnit();
			   Unit placementPoint = thisUnit;
			   int numParamsToInit = redef.getMethodByName("<init>").getParameterCount();
			   for(int i =0; i< numParamsToInit; i++){
				   placementPoint = units.getSuccOf(placementPoint);
			   }
			   placementPoint = units.getSuccOf(placementPoint); //once more bc the first statement in all inits is their super
			   placementPoint = units.getSuccOf(placementPoint); //just one more, since this is the insn AFTER super.init, and we're placing our hashtable inits on top of this ref
			   

               SootMethodRef hashtablePut = Scene.v().getMethod("<java.util.Hashtable: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef();
               units.insertBefore(Jimple.v().newAssignStmt(newclassref, Jimple.v().newNewExpr(newClass.getType())), placementPoint);
        units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(newclassref, newClass.getMethod("<init>", Arrays.asList(new Type[]{}), VoidType.v()).makeRef())), placementPoint);


               units.insertBefore(Jimple.v().newAssignStmt(hashtableOGToHost, Jimple.v().newStaticFieldRef(newClass.getFieldByName("originalToHostHashTable").makeRef())), placementPoint);
               units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(hashtableOGToHost, hashtablePut, Arrays.asList(new Value[]{thisref, newclassref}))), placementPoint);

               units.insertBefore(Jimple.v().newAssignStmt(hashtableHostToOG, Jimple.v().newStaticFieldRef(newClass.getFieldByName("hostToOriginalHashTable").makeRef())), placementPoint);
        units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(hashtableHostToOG, hashtablePut, Arrays.asList(new Value[]{newclassref, thisref}))), placementPoint);
               
               
       }

	/*
	 * Builds a lookup of a object
	 * referring to some redefinition class
	 * mapped to its host
	 */
	private Local lookup(SootClass newClass, Body body, PatchingChain<Unit> units, Unit insertBeforeHere, Value baseOfOriginalStmt){
		
		Local newClassRef = Jimple.v().newLocal("newClassRef", newClass.getType());
		body.getLocals().add(newClassRef);
		Local objecttemp = Jimple.v().newLocal("objecttemp", RefType.v("java.lang.Object"));
		body.getLocals().add(objecttemp);
		Local HT = Jimple.v().newLocal("HTFieldTemp", RefType.v("java.util.Hashtable"));
		body.getLocals().add(HT);


		units.insertBefore(Jimple.v().newAssignStmt(HT, Jimple.v().newStaticFieldRef(newClass.getFieldByName("originalToHostHashTable").makeRef())), insertBeforeHere);
		
		SootMethodRef mapGetter = Scene.v().getMethod("<java.util.Hashtable: java.lang.Object get(java.lang.Object)>").makeRef();
		units.insertBefore(Jimple.v().newAssignStmt(objecttemp, Jimple.v().newVirtualInvokeExpr(HT, mapGetter, Arrays.asList( new Value[]{baseOfOriginalStmt}))), insertBeforeHere);

		units.insertBefore(Jimple.v().newAssignStmt(newClassRef, Jimple.v().newCastExpr(objecttemp, newClass.getType())), insertBeforeHere);

		return newClassRef;
		
	}


}
