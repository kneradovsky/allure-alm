package ru.sbt.qa.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.yandex.qatools.allure.data.AllureBehavior;
import ru.yandex.qatools.allure.data.AllureFeature;
import ru.yandex.qatools.allure.data.AllureStory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.alm.rest.EntityType;
import com.hp.alm.rest.FieldType;
import com.hp.alm.rest.FieldsType;
import com.hp.alm.rest.ObjectFactory;


class AlmTestCase {
	String testId;
	AllureStory story;
	AlmTestCase(String tid,AllureStory st) {
		testId=tid;
		story=st;
	}
}

public class AlmReporter {
	AllureBehavior behav;
	List<AlmTestCase> almtcs;
	protected final static String fnBehav="behavior.json";
	Logger logger = LoggerFactory.getLogger(AlmReporter.class);
	
	public static void main(String[] args) {
		String baseUrl,dataFolder;
		Properties props = new Properties();
		try {
			props.load(AlmReporter.class.getResourceAsStream("/alm-report.properties"));
		} catch(IOException e) {
			e.printStackTrace();
		}
		if(args.length>=2) {
			dataFolder=args[0];
			baseUrl=args[1];
		} else {
			baseUrl=System.getProperty("baseUrl", "");
			dataFolder=System.getProperty("dataFolder","");
		}
		AlmReporter rep = new AlmReporter();
		rep.readReportData(dataFolder);
	}
	
	protected void readReportData(String reportFolder) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			behav = mapper.readValue(new File(reportFolder+File.separator+fnBehav), AllureBehavior.class);
			almtcs=new ArrayList<>();
			for(AllureFeature f : behav.getFeatures()) 
				for(AllureStory st : f.getStories()) {
					String title = st.getTitle();
					String[] tparts = title.split(" ");
					try {
						Integer testId=Integer.parseInt(tparts[0]);
						almtcs.add(new AlmTestCase(testId.toString(),st));
					} catch(NumberFormatException e) {
						logger.debug("The title '"+title+"' doesn't start with number", e);
					}
				}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected EntityType createEntity(AlmTestCase tc) {
		ObjectFactory fact = new ObjectFactory();
		EntityType ent = new EntityType()
			.withFields(new FieldsType()
				.withField(values)
		)
	}
	
	protected void generateAlmTestRuns(String repBaseURL) {
		
	}
}
