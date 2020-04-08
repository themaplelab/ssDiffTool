package differ;

import java.util.List;
import java.util.ArrayList;

class CheckSummary<T>{
	List<T> addedList = new ArrayList<T>();
	List<T> removedList = new ArrayList<T>();
	boolean changes;
	
	public void addAddedItem(T t){
		addedList.add(t);
	}

	public void addRemovedItem(T t){
		removedList.add(t);
    }


}
