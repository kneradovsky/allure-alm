package ru.sbt.qa.plugin;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import ru.sbt.qa.alm.RestConnector;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.sbt.qa.alm.TestSuiteFormation;


/**
 *
 * @author sbt-frantsuzov-sv
 */
@Mojo(name = "featureGen")
@Execute(goal = "featureGen",phase = LifecyclePhase.NONE)
public class TestSuiteFormatuonPlugin extends AbstractMojo {

    @Parameter(name = "almProperties",property = "almProperties",defaultValue = "/alm.properties")
    private String almProperties;

    @Parameter(defaultValue = "${project}",required = true, readonly = true)
    private MavenProject project;


    @Override
    public void execute() throws MojoExecutionException {
        //load properties
        Properties props = new Properties();
        Log log = getLog();
        InputStream is;
        List<Resource> resources = project.getTestResources();
        resources.addAll(project.getResources());
        // get the resources path of the project calling the Mojo
        URL[] resurls = resources.stream().map(it -> {
        try {
            return new File(it.getDirectory()).toURI().toURL();
        } catch(Exception e){
            log.debug("Error converting " + it + " to URL");
        } return ""; 
        }).toArray(c -> new URL[c]);
        
        ClassLoader rescl = URLClassLoader.newInstance(resurls);
        log.info("Loading properties from " + almProperties);
        //load properties from resources
        is = rescl.getResourceAsStream(almProperties);
        try {
            //if failed then load from the filesystem
            if(is == null) {
                is = new FileInputStream(almProperties);
            }
            props.load(new InputStreamReader(is, RestConnector.restCharset));
        } catch(IOException e) {
            throw new MojoExecutionException("Failed to load properties",e);
        }
        log.info("loading template");

        TestSuiteFormation reporter = new TestSuiteFormation();
        reporter.setAlmprops(props); //set properties
        try {
            reporter.createFeaturesSuite();
        } catch (Exception ex) {
            log.error("Error while creating feature suite\n", ex);
        }
    }
}
