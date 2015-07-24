package ru.sbt.qa.cli;

import org.apache.commons.io.IOUtils;
import ru.sbt.qa.alm.Reporter;
import ru.sbt.qa.alm.RestConnector;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;


public class AlmReporter {

	public static void main(String[] args) {
		String baseUrl,dataFolder;
		Properties props = new Properties();
		try {
			InputStream is;
			System.out.println(Arrays.toString(args));
			if(args.length>=3 && !(args[2]==null || args[2].isEmpty()))
				is=new FileInputStream(args[2]);
			else is=AlmReporter.class.getResourceAsStream("/alm-report.properties");
			props.load(new InputStreamReader(is,RestConnector.restCharset));
		} catch(IOException e) {
			System.out.println("Alm properties load error");
			e.printStackTrace();
			return;
		}
		String template="";
		try {
			Optional<String> commentpath=Optional.ofNullable(props.getProperty("alm.commentTemplate"));
			template=IOUtils.toString(AlmReporter.class.getResourceAsStream(commentpath.orElse("/alm-comment.html")),RestConnector.restCharset);
		} catch(Throwable e) {
			System.out.println("Comments template load error");
			e.printStackTrace();
			return;
		}
		if(args.length>=2) {
			dataFolder=args[0];
			baseUrl=args[1];
		} else {
			baseUrl=System.getProperty("baseUrl", "");
			dataFolder=System.getProperty("dataFolder","");
		}
		
		Reporter rep = new Reporter();
		rep.setAlmprops(props);
		rep.setTemplate(template);
		rep.readReportData(dataFolder);
		rep.generateAlmTestRuns(baseUrl);
		System.out.println("Done");
		System.exit(0);
	}
	

}
