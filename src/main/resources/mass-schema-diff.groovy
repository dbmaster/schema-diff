import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.inventory.model.Database
import com.branegy.service.core.exception.EntityNotFoundApiException
import com.branegy.service.core.search.CustomCriterion
import com.branegy.service.core.search.CustomCriterion.Operator
import com.branegy.dbmaster.model.RevEngineeringOptions
import com.branegy.service.core.QueryRequest
import com.branegy.dbmaster.sync.api.SyncPair.ChangeType
import com.branegy.email.EmailSender
import com.branegy.service.core.search.CustomCriterion

import com.thoughtworks.xstream.XStream

import java.io.*
import java.text.SimpleDateFormat
import java.text.DateFormat
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.Charsets
import org.apache.commons.io.FilenameUtils
import java.util.Locale
import org.apache.commons.io.FileUtils;
import com.branegy.dbmaster.sync.api.SyncService

def xstream = new XStream();
// TODO - use property
def p_storage_folder = com.branegy.util.DataDirHelper.getDataDir()+"/schema-diff";
logger.debug("Using storage folder ${p_storage_folder}")


def saveObjectToFile = { object, file_name ->
    File file = new File(file_name)
    file.getParentFile().mkdirs();
    def outStream = new FileOutputStream(file)
    try {
        if (object instanceof String){
            IOUtils.write((String)object, outStream, Charsets.UTF_8);
        } else {
            xstream.toXML(object,outStream)
        }
        outStream.flush();
    } finally {
        outStream.close()
    }
}


def loadModel = { server_name, db_name ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/model.dat"
    File file = new File(file_name)
    file.getParentFile().mkdirs();
    Model model = null;
    logger.debug("Loading model from file ${file_name}")
    if(file.exists() && !file.isDirectory()) {
        model = xstream.fromXML(file);
    } else {
        logger.debug("File does not exist ${file_name}")
    }
    return model
}

def saveModel = { server_name, model ->
    def db_name = model.getOptions().database
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/model.dat"
    saveObjectToFile(model, file_name)
}

def saveDiff = { server_name, db_name, syncSession, date ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/diff-"+
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(date)+ ".dat"
    saveObjectToFile(syncSession, file_name)
}

def saveErrorMessage = { server_name, db_name,  message ->
    def file_name = p_storage_folder + "/" + server_name + "/" + db_name + "/errors.txt";
    FileUtils.writeStringToFile(new File(file_name),
        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())+" "+message.replaceAll("\r\n|\n"," "),
        Charsets.UTF_8,true);
}


modelService = dbm.getService(ModelService.class)
syncService = dbm.getService(SyncService.class)
invService = dbm.getService(InventoryService.class)

def sdf = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT, Locale.US);

for (Database db:invService.getDatabaseList(new QueryRequest(p_database_query))) {
    if (db.getDatabaseName().equalsIgnoreCase("tempdb")) {
        continue;
    }
    try {
        Date version = new Date();

        RevEngineeringOptions options = new RevEngineeringOptions()
        options.database = db.getDatabaseName()

        logger.debug("Time is ${sdf.format(version)} (fetching new model)")

        def targetModel = modelService.fetchModel(db.getServerName(), options);

        logger.debug("Time is ${sdf.format(new Date())} (loading storedModel)")
        def sourceModel = loadModel(db.getServerName(), db.getDatabaseName());
        if (sourceModel == null) {
            logger.debug("Time is ${sdf.format(new Date())} (saving model)")
            saveModel(db.getServerName(), targetModel);
            logger.info("${db.getServerName()}.${db.getDatabaseName()}: new database")
            println "<div>${db.getServerName()}.${db.getDatabaseName()}: new database found</div>"
        } else {
            SyncSession syncSession = modelService.compareModel(sourceModel, targetModel);
            if (syncSession.getSyncResult().getChangeType() != com.branegy.dbmaster.sync.api.SyncPair.ChangeType.EQUALS){
                logger.debug("Time is ${sdf.format(new Date())} (saving model)")
                saveModel(db.getServerName(), targetModel);

                logger.debug("Time is ${sdf.format(new Date())} (generate sync)")
                String diff = syncService.generateSyncSessionPreviewHtml(syncSession, true);
                saveDiff(db.getServerName(), db.getDatabaseName(), diff, version);
                logger.info("${db.getServerName()}.${db.getDatabaseName()}: data structure changes found")
                println "<div>${db.getServerName()}.${db.getDatabaseName()}: data structure changes found</div>"
            } else {
                def file_name = p_storage_folder + "/" + db.getServerName() + "/" + db.getDatabaseName() + "/model.dat"
                new File(file_name).setLastModified(version.getTime())
                println "<div>${db.getServerName()}.${db.getDatabaseName()}: no data structure changes found</div>"
            }
        }
    } catch (Exception e) {
        // TODO below is a quick and dirty solution to work around skipping connectivity issues
        //      there can be other errors that shoud not be skipped
        if (p_ignore_nonOnlineDBs) {
            saveErrorMessage(db.getServerName(), db.getDatabaseName(), e.toString())
        }

        logger.error("${db.getServerName()}.${db.getDatabaseName()}: Error occurs", e)
        println "<div>${db.getServerName()}.${db.getDatabaseName()} error: ${e.getMessage()}</div>"
    }
}