// Follow RDBSource for inspiration
// Here we import the NETCONF Library and instantiate the client

package burp.ls;

import java.util.Iterator;

import burp.model.Iteration;
import burp.model.LogicalSource;

class YANGSource extends LogicalSource {

	public String endpoint;
	public String password;
	public String username;

	@Override
	public Iterator<Iteration> iterator() {
		return null;
	}

}
