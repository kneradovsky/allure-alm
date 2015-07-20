package ru.sbt.qa.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.sbt.qa.alm.AlmEntityUtils;
import ru.sbt.qa.alm.Reporter;
import ru.sbt.qa.alm.RestConnector;
import ru.yandex.qatools.allure.data.AllureBehavior;
import ru.yandex.qatools.allure.data.AllureFeature;
import ru.yandex.qatools.allure.data.AllureStory;
import ru.yandex.qatools.allure.data.AllureTestCaseInfo;
import ru.yandex.qatools.allure.data.Statistic;
import ru.yandex.qatools.allure.data.Time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.alm.rest.Entities;
import com.hp.alm.rest.Entity;
import com.hp.alm.rest.Field;
import com.hp.alm.rest.Field.Value;
import com.hp.alm.rest.Fields;


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
