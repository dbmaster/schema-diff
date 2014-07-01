import java.text.SimpleDateFormat

import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.email.EmailSender
import org.apache.commons.io.*
import javax.mail.Message.RecipientType;
import java.text.DateFormat
import java.util.Locale

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

def emailDf = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.US);
String[] to = p_emails.split("[,;]");

if (!emailBody.isEmpty()){
    email.createMessage(to[0], "Mass schema diff result for ${emailDf.format(version)}", "Please find database changes attached", true)
    email.addAttachment("changes.html", emailBody)
} else {
    email.createMessage(to[0], "Mass schema diff result for ${emailDf.format(version)}", "No changes was found", false)
}

for (int i=1; i<to.length; ++i){
    email.addRecepient(RecipientType.TO, to[i]);
}
email.sendMessage();