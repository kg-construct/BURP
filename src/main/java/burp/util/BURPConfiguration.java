package burp.util;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "burp")
public class BURPConfiguration {

	@Option(names = {"-h", "--help" }, usageHelp = true, description = "Display a help message")
	public boolean help = false;

	@Option(names= {"-m", "--mappingFile"}, description = "The RML mapping file", required = true)
	public String mappingFile = null;

	@Option(names= {"-o", "--outputFile"}, description = "The output file", required = false)
	public String outputFile = null;

	@Option(names = {"-b", "--baseIRI"}, description = "Used in resolving relative IRIs produced by the RML mapping" )
	public String baseIRI = null;
	
	public BURPConfiguration(String[] args) throws Exception {
		try {
			BURPConfiguration conf = CommandLine.populateCommand(this, args);
			if(conf.help) {
				new CommandLine(this).usage(System.out);
				System.exit(0);
			}
			
			if(conf.mappingFile == null) {
				throw new Exception("An RML mapping file is mandatory.");
			}
		} catch (CommandLine.ParameterException pe) {
			System.out.println(pe.getMessage());
			new CommandLine(this).usage(System.out);
			throw pe;
		}
	}
}