import java.io.IOException;

import org.apache.commons.io.Charsets;

import com.branegy.tools.api.HtmlPrinter;
import com.branegy.tools.impl.presenter.DirectHmltDataPresenter;

import groovy.io.FileType

import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*
import com.branegy.service.connection.api.ConnectionService
import com.branegy.service.connection.model.DatabaseConnection
import com.branegy.dbmaster.connection.JDBCDialect
import com.branegy.dbmaster.connection.Connector
import com.branegy.dbmaster.connection.ConnectionProvider

import org.slf4j.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils;

import io.dbmaster.testng.BaseServiceTestNGCase;
import io.dbmaster.testng.BaseToolTestNGCase;

import java.io.File;
import java.sql.Connection

import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.inject.Inject;

import com.branegy.persistence.update.SqlExec


public class SchemaDiffTestIT extends BaseServiceTestNGCase{
    @Inject
    ConnectionService connectionSrv;
    
    @Inject
    InventoryService invService;
    
    @Inject
    SyncService syncService;
    
    @Inject
    ModelService modelService;

    def runSQL = { connectionName, targetDB, fileName  ->
        DatabaseConnection dbConnection = connectionSrv.findByName(connectionName)

        Connector connector = ConnectionProvider.getConnector(dbConnection)
        JDBCDialect dialect = (JDBCDialect)connector.connect()
        Connection connection = dialect.getProvider().getJdbcConnection(targetDB)
        try{
            connection.setAutoCommit(true)
            new SqlExec().execute(FileUtils.readFileToString(new File(fileName),"UTF-8"), connection)
        } finally {
            connection.close();
        }
    }
    
    protected final File getTestResourcesDir() {
        return new File(new File(System.getProperty("artifact.bundle.path")).getParentFile().getParentFile(),"src/test/resources");
    }
    
    protected final File getBuildDir() {
        return new File(new File(System.getProperty("artifact.bundle.path")).getParentFile().getParentFile(),"target");
    }
    
    @Test
    public void testSchemaDiff() {
        def targetDB =       getTestProperty("schema-diff.target_db");
        def connectionName = getTestProperty("schema-diff.connection")
        def testFolder =     getTestResourcesDir().getPath();
        def testReportFile = new File(getBuildDir(),"/schema-diff-report.html");
        
        OutputStream os = null;
        PrintWriter writer = null;
        try {
            os = FileUtils.openOutputStream(testReportFile);
            writer = new PrintWriter(os);
            writer.print("<!DOCTYPE html>");
            writer.print("<html><head>");
            writer.print("<style>");
            writer.print(IOUtils.toString(DirectHmltDataPresenter.class.getResource("extra.css"), Charsets.UTF_8));
            writer.print("</style>");
            writer.print("</head><body>");
            writer.print("<div>");
                
            Model originalModel = null
            def dir = new File("${testFolder}/tests/queries")
            dir.eachFileRecurse (FileType.FILES) { file ->
                println ("Processing ${file.name}")
                writer.println("------------------------- Processing test case for file: ${file.name}")
                writer.println("<pre>")
                writer.println(file.text)
                writer.println("</pre>")
                writer.println("------------------------- Sync table --------------------------------")
            
                println ("Cleanup/initiate database with  000_setup.sql")
                runSQL(connectionName, targetDB, "${testFolder}/000_cleanup.sql")
                runSQL(connectionName, targetDB, "${testFolder}/000_setup.sql")
            
                RevEngineeringOptions options = new RevEngineeringOptions();
                options.database = targetDB
                options.importProcedures = true
                options.importViews  = true
            
                if (originalModel == null) {
                    println("Loading initial model")
                    originalModel = modelService.fetchModel(connectionName, options)
                }
            
                runSQL(connectionName, targetDB, file.canonicalPath)
            
                def changedModel = modelService.fetchModel(connectionName, options)
            
                def syncSession = modelService.compareModel(originalModel, changedModel)
            
                def template = "/preview-model-generator.groovy"
                def preview = syncService.generateSyncSessionPreviewHtml(template, syncSession, true)
            
                writer.println("<div>")
                writer.println(preview)
                writer.println("</div>")
            }
        }
        finally {
            if (writer!=null) {
                writer.print("</div>");
                writer.print("</body></html>");
                writer.flush();
            }
            IOUtils.closeQuietly(os);
        }
    }
}

