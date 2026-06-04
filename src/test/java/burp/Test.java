package burp;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public class Test {

	public static void main(String[] args) {
		List<List<Integer>> list = new ArrayList<List<Integer>>();
		list.add(List.of(1, 2));
		list.add(List.of(3, 4));
		
		System.out.println(Lists.cartesianProduct(list));
		
	}

}
