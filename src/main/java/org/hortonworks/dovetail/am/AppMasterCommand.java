package org.hortonworks.dovetail.am;

import java.io.File;
import java.util.List;
import java.util.Vector;

import org.apache.hadoop.yarn.api.ApplicationConstants.Environment;
import org.hortonworks.dovetail.util.DovetailConfiguration;

public class AppMasterCommand {

	public static List<String> getCommands(DovetailConfiguration conf) {
		Vector<String> vargsFinal = new Vector<String>(1);
		vargsFinal.add(getAmCommand(conf));
		return vargsFinal;
	}

	private static String getAmCommand(DovetailConfiguration conf) {

		Vector<String> vargs = new Vector<String>(9);

		vargs.add(Environment.JAVA_HOME.$() + File.separator + "bin"
				+ File.separator + "java");

		vargs.add(String.format("-D%s=%s",
				DovetailConfiguration.JBOSS_DIST_FILE,
				conf.get(DovetailConfiguration.JBOSS_DIST_FILE)));
		
		vargs.add(String.format("-D%s=%s",
				DovetailConfiguration.DOVETAIL_AM_JAR,
				conf.get(DovetailConfiguration.DOVETAIL_AM_JAR)));

		vargs.add("-Xmx"
				+ String.valueOf(conf.getInt(
						DovetailConfiguration.DOVETAIL_AM_MEMORY,
						DovetailConfiguration.DEFAULT_DOVETAIL_AM_MEMORY))
				+ "m");
		vargs.add(AppMaster.class.getName());

		vargs.add("1>" + conf.get(DovetailConfiguration.DOVETAIL_LOG_DIR)
				+ "/DovetailApplicationMaster.stdout");
		vargs.add("2>" + conf.get(DovetailConfiguration.DOVETAIL_LOG_DIR)
				+ "/DovetailApplicationMaster.stderr");

		StringBuilder mergedCommand = new StringBuilder();
		for (CharSequence str : vargs) {
			mergedCommand.append(str).append(" ");
		}
		return mergedCommand.toString();

	}
}