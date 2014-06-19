import java.text.SimpleDateFormat

import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.email.EmailSender

def fileToString = { file ->
    return IOUtils.toString(file.toURI().toURL(), Charsets.UTF_8)
}

Date versionA = null;
Date versionB = null;

def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
def configFile = new File(p_storage_folder + "/" + p_config_name);
if (configFile.exists() && configFile.isFile()){
   versionA = sdf.parse(fileToString(configFile));
}

print new MassSchemaDiffHistory(logger, versionA, versionB, dbm)
    .getHistory(p_database_query,p_storage_folder)


