package burp;

public class Test {
	
    public static void main(String[] args) throws Exception {
    	String base = "./src/test/burp/rml-core/";
    	String f = "RMLTC0002e-JSON";
    	Main.main(new String[] { "-m", base + f + "/mapping.ttl", "-b", "http://example.com/base/" });
    	
//		System.out.println(String.format("Now processing %s", f));
//		String m = new File(base+ f, "mapping.ttl").getAbsolutePath().toString();
//		String r = Files.createTempFile(null, ".nq").toString();
//		System.out.println(String.format("Writing output to %s", r));
//		
//		if(new File(base + f, "output.nq").exists()) {
//			System.out.println("This test should generate a graph.");
//	    	String o = new File(base + f, "output.nq").getAbsolutePath().toString();
//	
//			int exit = Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" });
//	
//			Model expected = RDFDataMgr.loadModel(o);
//			Model actual = RDFDataMgr.loadModel(r);
//			
//			if(!expected.isIsomorphicWith(actual)) {
//				expected.write(System.out, "NQ");
//				System.out.println();
//				actual.write(System.out, "NQ");
//			}
//			
//			System.err.println(exit);
//			
//			assertEquals(0, exit);
//			
//			System.out.println(expected.isIsomorphicWith(actual) ? "OK" : "NOK");
//			
//			assertTrue(expected.isIsomorphicWith(actual));
//			
//		} else {
//			System.out.println("This test should NOT generate a graph.");
//			int exit = Main.doMain(new String[] { "-m", m, "-o", r });
//			System.out.println(Files.size(Paths.get(r)) == 0 ? "OK" : "NOK");
//	    	Model actual = RDFDataMgr.loadModel(r);
//			actual.write(System.out, "NQ");
//			
//			assertTrue(exit > 0);
//			assertTrue(Files.size(Paths.get(r)) == 0);
//		}
//		
//		System.out.println();	
    }
    
}
