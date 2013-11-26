package hudson.plugins.emailext.plugins.content;

import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.emailext.plugins.EmailToken;
import hudson.plugins.emailext.ExtendedEmailPublisher;
import hudson.plugins.emailext.ExtendedEmailPublisherDescriptor;
import hudson.tasks.Mailer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

@EmailToken
public class JellyScriptContent extends DataBoundTokenMacro {

    public static final String MACRO_NAME = "JELLY_SCRIPT";
    private static final String DEFAULT_HTML_TEMPLATE_NAME = "html";
    private static final String DEFAULT_TEXT_TEMPLATE_NAME = "text";
    private static final String DEFAULT_TEMPLATE_NAME = DEFAULT_HTML_TEMPLATE_NAME;
    private static final String EMAIL_TEMPLATES_DIRECTORY = "email-templates";
    
    @Parameter
    public String template = DEFAULT_TEMPLATE_NAME;

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals(MACRO_NAME);
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> build, TaskListener listener, String macroName)
            throws MacroEvaluationException, IOException, InterruptedException {
        InputStream inputStream = null;

        try {
            inputStream = getTemplateInputStream(template);
            return renderContent(build, inputStream);
        } catch (JellyException e) {
            return "JellyException: " + e.getMessage();
        } catch (FileNotFoundException e) {
            String missingTemplateError = generateMissingTemplate(template);
            return missingTemplateError;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private String generateMissingTemplate(String template) {
        return "Jelly script [" + template + "] was not found in $JENKINS_HOME/" + EMAIL_TEMPLATES_DIRECTORY + ".";
    }

    /**
     * Try to get the template from the classpath first before trying the file
     * system.
     *
     * @param templateName
     * @return
     * @throws java.io.FileNotFoundException
     */
    private InputStream getTemplateInputStream(String templateName)
            throws FileNotFoundException {
        // add .jelly if needed
        if (!templateName.endsWith(".jelly")) {
            templateName += ".jelly";
        }
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "hudson/plugins/emailext/templates/" + templateName);

        if (inputStream == null) {
            final File templatesFolder = new File(Hudson.getInstance().getRootDir(), EMAIL_TEMPLATES_DIRECTORY);
            final File templateFile = new File(templatesFolder, templateName);
            inputStream = new FileInputStream(templateFile);
        }

        return inputStream;
    }

    private String renderContent(AbstractBuild<?, ?> build, InputStream inputStream)
            throws JellyException, IOException {
        JellyContext context = createContext(new ScriptContentBuildWrapper(build), build);
        Script script = context.compileScript(new InputSource(inputStream));
        if (script != null) {
            return convert(build, context, script);
        }
        return null;
    }

    private String convert(AbstractBuild<?, ?> build, JellyContext context, Script script)
            throws JellyTagException, IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(16 * 1024);
        XMLOutput xmlOutput = XMLOutput.createXMLOutput(output);
        script.run(context, xmlOutput);
        xmlOutput.flush();
        xmlOutput.close();
        output.close();
        return output.toString(getCharset(build));
    }

    private JellyContext createContext(Object it, AbstractBuild<?, ?> build) {
        JellyContext context = new JellyContext();
        ExtendedEmailPublisherDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        context.setVariable("it", it);
        context.setVariable("build", build);
        context.setVariable("project", build.getParent());
        context.setVariable("rooturl", descriptor.getHudsonUrl());
        return context;
    }

    private String getCharset(AbstractBuild<?, ?> build) {
        String charset = Mailer.descriptor().getCharset();
        ExtendedEmailPublisherDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        String overrideCharset = descriptor.getCharset();
        if (overrideCharset != null) {
            charset = overrideCharset;
        }
        return charset;
    }
}
