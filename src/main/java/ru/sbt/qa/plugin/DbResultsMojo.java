package ru.sbt.qa.plugin;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.settings.Settings;
import ru.yandex.qatools.allure.data.utils.BadXmlCharacterFilterReader;
import ru.yandex.qatools.allure.model.Status;
import ru.yandex.qatools.allure.model.TestCaseResult;
import ru.yandex.qatools.allure.model.TestSuiteResult;

import javax.xml.bind.JAXB;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by sbt-neradovskiy-kl on 22.07.2015.
 * Maven report plugin class to parse allure results and generate run report.
 * Run report is the record [group-id,artifact-id,run time,features, test cases, test steps, result]
 */
@Mojo(name = "dbreport",defaultPhase = LifecyclePhase.SITE)
public class DbResultsMojo extends AbstractMavenReport {
    @Parameter(defaultValue = "${project}",required = true,readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${settings}")
    private Settings settings;


    @Component
    private Renderer siteRenderer;

    @Parameter(defaultValue = "${basedir}/target/allure-results/")
    private File allureResults;

    @Parameter(required = true)
    private String databaseUrl;

    @Parameter(required = false,defaultValue = "oatdb")
    private String databaseId;

    @Parameter(defaultValue = "",required = false)
    private String username;

    @Parameter(defaultValue = "",required = false)
    private String password;

    private Log log;

    @Parameter(defaultValue = "${project.reporting.outputDirectory}/dblog",required = false)
    private File outputDirectory;



    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory.getAbsolutePath();
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }


    @Override
    public String getOutputName() {
        return "OATDBReport";
    }

    @Override
    public String getName(Locale locale) {
        return "OATDBReport";
    }

    @Override
    public String getDescription(Locale locale) {
        return "Store run data to the OAT result db";
    }

    /**
     * Generates run report and stores it to the database
     * @param locale
     * @throws MavenReportException
     */
    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        log = getLog();
        String group = project.getGroupId();
        String artifact = project.getArtifactId();
        boolean isPassed=true;
        Integer features=0,cases=0,steps=0;
        List<TestSuiteResult> results = parseAllureResults();
        Set<String> featureNames = new HashSet<>();
        cases = results.size();
        for(TestSuiteResult res : results) {
            if(res==null) continue; //skip null testcases
            String featName = res.getLabels().stream().filter(it -> it.getName().equals("feature")).findFirst().get().getValue();
            featureNames.add(featName);
            steps += res.getTestCases().size();
            for(TestCaseResult tc : res.getTestCases()) {
                steps+=tc.getSteps().size();
                if(isPassed) isPassed=(tc.getStatus() == Status.PASSED);
            }
        }
        features = featureNames.size();
        log.info("Testrun stats: features="+features+" ,cases="+cases+" ,steps="+steps);
    }

    /**
     * Scans allure-results folder for result files. Parses each allure result file to TestSuiteResult
     * @return List of TestSuiteResults
     * @throws MavenReportException
     */
    protected List<TestSuiteResult> parseAllureResults() throws MavenReportException{
        try {
            String[] results = allureResults.list((d, f) -> f.endsWith("testsuite.xml"));
            if(results==null || results.length==0) {
                log.info("Found [0] results");
                return new ArrayList<TestSuiteResult>();
            }
            log.info("Found ["+results.length+"] results");
            List<TestSuiteResult> res = Stream.of(results).map(fn -> {
                        try {
                            log.info("Processing file:" + fn);
                            return JAXB.unmarshal(new BadXmlCharacterFilterReader(new FileReader(allureResults.getAbsolutePath()+"/"+fn)), TestSuiteResult.class);
                        } catch (IOException e) {
                            log.error("ErrorReading file:" + fn);
                            return null;
                        }
                    }
            ).collect(Collectors.toList());
            return res;
        } catch(Exception e) {
            log.error("parseAllureResults exception",e);
            throw new MavenReportException("parseAllureResults",e);
        }
    }

}

