package differ;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class SemanticOptions extends Options {


	public SemanticOptions() {
		Option redefinitionClass = Option.builder().longOpt("redefcp").hasArg().required().desc("Specify the classpath of the redefinition classes.").build();
		addOption(redefinitionClass);

		Option originalClass = Option.builder().longOpt("originalclasslist").hasArg().desc("Specify a file containing the list of original classes.").build();
        addOption(originalClass);

		Option firstDest = Option.builder().longOpt("renameDestination").hasArg().desc("Specify the first directory for the renamed class to go to.").build();
		addOption(firstDest);
		
		Option altDest = Option.builder().longOpt("finalDestination").hasArg().desc("Specify the directory for the adapter output to go to.").build();
		addOption(altDest);

		//bit odd to have arg, but bc of parsing strategy in relaxed parser to sort our opts from soot opts.
		Option runRename = Option.builder().longOpt("runRename").hasArg().desc("Specify whether the rename phase should be run. Default false.").build();
		addOption(runRename);

		Option mainClass = Option.builder().longOpt("mainClass").hasArg().desc("Specify the actual main class of the application, to be provided to Soot AFTER Soot startup has occurred.").build();
        addOption(mainClass);

		Option differClasspath = Option.builder().longOpt("differClasspath").hasArg().desc("Specify the classpath for the patch adapter's run of Soot. May be different than that of a Cogni+Soot run, if patch adapter is run from HOTFIXER.").build();
		addOption(differClasspath);
		
	}

}
