import java.text.SimpleDateFormat

import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.email.EmailSender
import org.apache.commons.io.*
import javax.mail.Message.RecipientType
import java.text.DateFormat
import java.util.Locale
import com.branegy.service.base.api.ProjectService


def fileToString = { file ->
    return IOUtils.toString(file.toURI().toURL(), Charsets.UTF_8)
}

def stringToFile = { file, content -> 
    return FileUtils.writeStringToFile(file, content, Charsets.UTF_8)
}

Date versionA = null;
Date versionB = null;
Date version = new Date();
def p_storage_folder = com.branegy.util.DataDirHelper.getDataDir()+"/schema-diff";

def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
def configFile = new File(p_storage_folder + "/" + p_config_name);
if (configFile.exists() && configFile.isFile()){
   versionA = sdf.parse(fileToString(configFile));
}

String emailBody = new MassSchemaDiffHistory(logger, versionA, versionB, dbm)
    .getHistory(p_database_query,p_storage_folder)
    

stringToFile(configFile, sdf.format(version));
EmailSender email = dbm.getService(EmailSender.class);

def emailDf = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US);
String[] to = p_emails.split("[,;]");



def projectName = dbm.getService(ProjectService.class).getCurrentProject().getName();
def subject     = "Project ${projectName}: schema change report";
def sendEmail   = false

if (!emailBody.isEmpty()) {
    email.createMessage(
        to[0], 
        subject, 
        "${emailDf.format(version)}. Please find database changes attached.", 
        true)
    email.addAttachment("changes.html", emailBody)
    sendEmail = true
} else if (p_notify_nochanges) {
    email.createMessage(
        to[0], 
        subject, 
        "${emailDf.format(version)}. No changes was found", 
        false)
   sendEmail = true
}

if (sendEmail) {
    for (int i=1; i<to.length; ++i) {
        email.addRecepient(RecipientType.TO, to[i])
    }
    email.sendMessage()
}
