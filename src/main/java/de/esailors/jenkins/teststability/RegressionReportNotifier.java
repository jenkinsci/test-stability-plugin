package de.esailors.jenkins.teststability;

import java.util.logging.*; 

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.Mailer;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;

import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.StringTokenizer;

import java.util.HashMap;
import java.util.Map;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.apache.http.*;
import org.apache.http.message.*;

/**
 * The RegressionReportNotifier provides the ability to send mails/texts containing information
 * about regressed tests in current build to a specified list of recipients
 *
 * @author eller86 (Kengo TODA), KGBTeam_UIUC
 */
public class RegressionReportNotifier {
	
    private static final Logger myLog = Logger.getLogger(RegressionReportNotifier.class.getName());
	
    static interface MailSender {
        void send(MimeMessage message) throws MessagingException;
    }
    
    private MailSender mailSender = new RegressionReportNotifier.MailSender() {
        @Override
        public void send(MimeMessage message) throws MessagingException {
            Transport.send(message);
        }
    };

    private Map<String,CircularStabilityHistory> regressions = new HashMap<String,CircularStabilityHistory>();

    /**
    * The addResult method is used to incrementally fill list of regressed test to be included in the mail 
    *
    * @param testName The name of the test to be added
    * @param author The author of the build
    */
    public void addResult(String testName, CircularStabilityHistory history) {
        regressions.put(testName, history);
    }

    /**
    * The mailReport method sends a mail the to list of recipients passed as argument, containing
    * the list of regressed tests, and the author of the build 
    *
    * @param testName The name of the test to be added
    * @param author The author of the build
    *
    * @throws MessagingException
    */
    public void mailReport(String recipients, String author,
            BuildListener listener, AbstractBuild<?, ?> build)
            throws MessagingException {
        if (regressions.isEmpty()) {
            return;
        }

        // TODO link to test result page
        StringBuilder builder = new StringBuilder();
        String rootUrl = "";
        Session session = null;
        InternetAddress adminAddress = null;
        if (Jenkins.getInstance() != null) {
            rootUrl = Jenkins.getInstance().getRootUrl();
            session = Mailer.descriptor().createSession();
            adminAddress = new InternetAddress(
                    JenkinsLocationConfiguration.get().getAdminAddress());
        }
        builder.append(Util.encode(rootUrl));
        builder.append(Util.encode(build.getUrl()));
        builder.append("\n\n");
        builder.append(regressions.size() + " regressions found. Author: " + author);
        builder.append("\n");
        for (Map.Entry<String, CircularStabilityHistory> e : regressions.entrySet()) {
            builder.append("  ");
            builder.append(e.getKey());
            builder.append(" ");
            CircularStabilityHistory h = e.getValue();
            if (h != null){
                builder.append(String.format("Failed %d times in the last %d runs. Flakiness: %d%%, Stability: %d%%,", h.getFailed(), h.getSize(), h.getFlakiness(), h.getStability()));
            } 
            builder.append("\n");
        }

        List<Address> recipentList = parse(recipients, listener);

        MimeMessage message = new MimeMessage(session);
        //Add some better mail subject 
        message.setSubject("Regression Report");
        message.setRecipients(RecipientType.TO,
                recipentList.toArray(new Address[recipentList.size()]));
        message.setContent("", "text/plain");
        message.setFrom(adminAddress);
        message.setText(builder.toString());
        message.setSentDate(new Date());

        mailSender.send(message);
    }

   

    private List<Address> parse(String recipients, BuildListener listener) {
        List<Address> list = new ArrayList<Address>();
        List<String> recipAddresses = Arrays.asList(recipients.split(","));

        for(String address: recipAddresses){
            try {
                list.add(new InternetAddress(address));
            } catch (AddressException e) {
                e.printStackTrace(listener.error(e.getMessage()));
            }  
        }
        return list;
    }

}
