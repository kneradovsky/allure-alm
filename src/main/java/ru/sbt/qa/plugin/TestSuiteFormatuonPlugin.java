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
 * Created by sbt-neradovskiy-kl on 20.07.2015.
 */
@Mojo(name= "ololo")
@Execute(goal= "ololo",phase = LifecyclePhase.NONE)
public class TestSuiteFormatuonPlugin extends AbstractMojo {

    @Parameter(name="almProperties",property = "almProperties",defaultValue = "/alm.properties")
    private String almProperties;

    @Parameter(property = "commentTemplate",name="commentTemplate",defaultValue = "/comment.html")
    private String commentTemplate;

    @Parameter(property = "dataFolder",required=false)
    private File dataFolder;

    @Parameter(property = "baseUrl",required = true)
    private URL baseUrl;

    @Parameter(defaultValue = "${project}",required = true, readonly = true)
    private MavenProject project;


    @Override
    public void execute() throws MojoExecutionException {
        //load properties
        Properties props = new Properties();
        String template="";
        Log log = getLog();
        InputStream is;
        List<Resource> resources = project.getTestResources();
        resources.addAll(project.getResources());
        // get the resources path of the project calling the Mojo
        URL[] resurls = resources.stream().map(it -> {
            try {return new File(it.getDirectory()).toURI().toURL();}
            catch(Exception e){log.debug("Error converting "+it+ " to URL");}
            return "";
        }).toArray(c -> new URL[c]);
        ClassLoader rescl = URLClassLoader.newInstance(resurls);
        log.info("Loading properties from "+ almProperties);
        //load properties from resources
        is=rescl.getResourceAsStream(almProperties);
        try {
            //if failed then load from the filesystem
            if(is==null) is=new FileInputStream(almProperties);
            props.load(new InputStreamReader(is, RestConnector.restCharset));
        } catch(IOException e) {
            throw new MojoExecutionException("Failed to load properties",e);
        }
        log.info("loading template");
        /*
        //loading template file from resources
        is = rescl.getResourceAsStream(commentTemplate);
        try {
            //if failed then load from the filesystem
            if(is==null) is=new FileInputStream(commentTemplate);
            template = IOUtil.toString(is);
        } catch(IOException e) {
            throw new MojoExecutionException("Failed to load template",e);
        } */

        TestSuiteFormation reporter = new TestSuiteFormation();
        reporter.setAlmprops(props); //set properties
        try {
            reporter.createFeaturesSuite("301");
        } catch (Exception ex) {
            Logger.getLogger(TestSuiteFormatuonPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
