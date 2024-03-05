package burp;

import java.util.ArrayList;
import java.util.List;

import com.github.jsonldjava.shaded.com.google.common.collect.Lists;

public class Test {

	public static void main(String[] args) {
		List<List<Integer>> list = new ArrayList<List<Integer>>();
		list.add(List.of(1, 2));
		list.add(List.of(3, 4));
		
		System.out.println(Lists.cartesianProduct(list));
		
	}

}
